package com.outputhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.storage.StorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-outputhub-007·022: 같은 원본 파일명 재접수와 트랜잭션 롤백으로 old 폴더에 고립된 최신본 복구 검증.
 * 같은 파일명 재업로드가 이전 버전의 저장 파일을 덮어쓰거나 옮겨 버리면 검토완료가 실패한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Transactional
class VersionRegistrationRecoveryTest {

    private static final Path STORAGE_ROOT = createTempStorageRoot();

    @Autowired
    VersionRegistrationService versionRegistration;
    @Autowired
    StorageService storage;
    @Autowired
    Project.Repository projects;
    @Autowired
    ChecklistItem.Repository items;
    @Autowired
    Submission.Repository submissions;
    @Autowired
    Deliverable.Repository deliverables;

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("outputhub.storage-root", () -> STORAGE_ROOT.toString());
    }

    @Test
    @DisplayName("AC-007-x: 같은 원본 파일명을 다시 접수해도 이전 버전의 저장 파일이 덮여쓰이지 않는다")
    void keepPreviousVersionFileWhenSameNameReuploaded() throws IOException {
        // Given: 같은 원본 파일명으로 버전 1이 등록된 행
        Project project = projects.save(new Project("재접수 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission first = store(project, "요구사항 정의서.txt", "1판 본문");
        versionRegistration.register(item, first, "최초 접수");

        // When: 내용을 바꿔 같은 원본 파일명으로 다시 접수해 등록한다
        Submission second = store(project, "요구사항 정의서.txt", "2판 본문");
        versionRegistration.register(item, second, "재접수");

        // Then: 두 접수의 저장 경로가 다르고, 각 버전의 원본 내용이 그대로 보존된다
        assertThat(first.getStoredPath()).isNotEqualTo(second.getStoredPath());
        assertThat(Files.readString(Path.of(first.getStoredPath()))).isEqualTo("1판 본문");
        assertThat(Files.readString(Path.of(second.getStoredPath()))).isEqualTo("2판 본문");
    }

    @Test
    @DisplayName("AC-022-x: 최신본 파일이 old 폴더에 고립돼 있어도 검토완료 시 되돌려 v1.0으로 확정한다")
    void confirmRecoversStrandedLatestFile() throws IOException {
        // Given: 버전 1이 등록됐지만 롤백처럼 원본이 old 폴더로만 옮겨진 행
        Project project = projects.save(new Project("고립 복구 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission submission = store(project, "요구사항 정의서.txt", "정의서 본문");
        versionRegistration.register(item, submission, "최초 접수");
        Path stranded = storage.roundFolder(project.getName()).resolve("old")
                .resolve(Path.of(submission.getStoredPath()).getFileName());
        Files.createDirectories(stranded.getParent());
        Files.move(Path.of(submission.getStoredPath()), stranded);

        // When: 검토완료로 최신본 파일명을 확정 규칙에 맞게 바꾼다
        String confirmedFileName = versionRegistration.renameLatestToConfirmed(item);

        // Then: old의 원본을 되돌려 규칙 파일명으로 바꾸고 내용과 카운터가 맞다
        assertThat(confirmedFileName).isEqualTo("요구사항 정의서_v1.0.txt");
        assertThat(Path.of(submission.getStoredPath()).getFileName().toString())
                .isEqualTo("요구사항 정의서_v1.0.txt");
        assertThat(Files.readString(Path.of(submission.getStoredPath()))).isEqualTo("정의서 본문");
        assertThat(deliverables.findByItemId(item.getId()).orElseThrow().getLatestVersionNo())
                .isEqualTo(Deliverable.CONFIRMED_VERSION_NO);
    }

    private Submission store(Project project, String fileName, String content) {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        StorageService.StoredFile stored = storage.store(project.getName(), file);
        return submissions.save(new Submission(project, fileName, stored.path().toString(),
                "sha-" + content, LocalDateTime.now()));
    }

    private static Path createTempStorageRoot() {
        try {
            return Files.createTempDirectory("outputhub-storage-test");
        } catch (IOException e) {
            throw new IllegalStateException("테스트 저장소를 만들 수 없습니다", e);
        }
    }
}
