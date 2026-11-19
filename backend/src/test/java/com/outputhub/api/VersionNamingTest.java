package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import com.outputhub.service.VersionRegistrationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.transaction.annotation.Transactional;

/** REQ-outputhub-022 명명 규칙 검증: 버전 표기와 수락·검토완료 파일명 규칙. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Transactional
class VersionNamingTest {

    private static final LocalDate ACCEPTED_ON = LocalDate.of(2026, 11, 19);

    @Autowired
    VersionRegistrationService versionRegistration;
    @Autowired
    Project.Repository projects;
    @Autowired
    ChecklistItem.Repository items;
    @Autowired
    Submission.Repository submissions;
    @Autowired
    Deliverable.Repository deliverables;
    @Autowired
    Version.Repository versions;

    @Test
    @DisplayName("버전 번호(백분위)는 0.1·0.11·1.0·1.01·1.1 형태로 표기된다")
    void formatVersionLabel() {
        assertThat(VersionNaming.format(10)).isEqualTo("0.1");
        assertThat(VersionNaming.format(11)).isEqualTo("0.11");
        assertThat(VersionNaming.format(12)).isEqualTo("0.12");
        assertThat(VersionNaming.format(100)).isEqualTo("1.0");
        assertThat(VersionNaming.format(101)).isEqualTo("1.01");
        assertThat(VersionNaming.format(110)).isEqualTo("1.1");
    }

    @Test
    @DisplayName("수락 파일명은 {항목}_v{버전}_{YYYYMMDD}.{확장자}, 검토완료 파일명은 {항목}_v1.0.{확장자}다")
    void buildRuleFileNames() {
        assertThat(VersionNaming.versionedFileName("견적서", 10, "견적서_제출.pdf", ACCEPTED_ON))
                .isEqualTo("견적서_v0.1_20261119.pdf");
        assertThat(VersionNaming.versionedFileName("요구사항 정의서", 11, "정의서_v2.xlsx", ACCEPTED_ON))
                .isEqualTo("요구사항 정의서_v0.11_20261119.xlsx");
        assertThat(VersionNaming.confirmedFileName("견적서", "견적서_제출.pdf"))
                .isEqualTo("견적서_v1.0.pdf");
        assertThat(VersionNaming.versionedFileName("WBS", 10, "활동목록", ACCEPTED_ON))
                .isEqualTo("WBS_v0.1_20261119");
    }

    @Test
    @DisplayName("TC-13: 수락 시 원본 파일명이 {항목}_v0.1_{날짜} 로 바뀌고, 검토완료 시 v1.0 으로 바뀐다")
    void acceptRenamesFileAndConfirmMarksV1() throws IOException {
        // Given: 접수 상태의 산출물 행과 최초 접수 파일
        Project project = projects.save(new Project("명명 규칙 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission first = storeSubmission(project, "요구사항 정의서.txt", "최초 본문");

        // When: 수락으로 버전을 등록한다
        Version accepted = versionRegistration.register(item, first, "최초 접수");

        // Then: 파일명이 {항목}_v0.1_{오늘 날짜}.txt 로 바뀌고 실제 파일도 함께 바뀐다
        assertThat(accepted.getVersionNo()).isEqualTo(Deliverable.FIRST_VERSION_NO);
        assertThat(accepted.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.1_").endsWith(".txt");
        assertThat(Files.exists(storageFileOf(accepted))).isTrue();
        assertThat(Files.readString(storageFileOf(accepted))).isEqualTo("최초 본문");

        // When: 검토완료 처리한다
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        item.transitionTo(ChecklistItem.Status.CONFIRMED);
        String confirmed = versionRegistration.renameLatestToConfirmed(item);

        // Then: 최신본 파일명이 {항목}_v1.0.txt 로 바뀌고, 다음 수락 버전은 v1.01부터 시작한다
        assertThat(confirmed).isEqualTo("요구사항 정의서_v1.0.txt");
        assertThat(Files.readString(storageFileOf(accepted))).isEqualTo("최초 본문");
        assertThat(deliverables.findByItemId(item.getId()).orElseThrow().getLatestVersionNo())
                .isEqualTo(Deliverable.CONFIRMED_VERSION_NO);

        Submission second = storeSubmission(project, "요구사항 정의서_수정.txt", "수정 본문");
        Version reaccepted = versionRegistration.register(item, second, "재수락");
        assertThat(reaccepted.getVersionNo()).isEqualTo(Deliverable.CONFIRMED_VERSION_NO + 1);
        assertThat(reaccepted.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v1.01_").endsWith(".txt");
    }

    @Test
    @DisplayName("재제출 시 이전 버전(v0.1)이 old 폴더로 수집되고 최신본은 v0.11로 등록된다")
    void resubmitCollectsRenamedPreviousVersion() throws IOException {
        // Given: v0.1 수락 상태
        Project project = projects.save(new Project("재제출 명명 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission first = storeSubmission(project, "요구사항 정의서.txt", "최초 본문");
        versionRegistration.register(item, first, "최초 접수");

        // When: 다른 내용의 파일을 재제출 수락한다
        Submission second = storeSubmission(project, "요구사항 정의서_수정.txt", "수정 본문");
        Version latest = versionRegistration.register(item, second, "재제출 접수");

        // Then: 최신본은 v0.11, 이전 버전은 v0.1 파일명 그대로 old 폴더에 있다
        assertThat(latest.getVersionNo()).isEqualTo(11);
        assertThat(latest.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.11_").endsWith(".txt");
        Version oldVersion = versions.findByDeliverableIdOrderByVersionNoDesc(
                        deliverables.findByItemId(item.getId()).orElseThrow().getId()).stream()
                .filter(Version::isOld)
                .findFirst()
                .orElseThrow();
        assertThat(oldVersion.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.1_").endsWith(".txt");
        assertThat(Path.of(oldVersion.getSubmission().getStoredPath()).getParent().getFileName().toString())
                .isEqualTo("old");
    }

    private Path storageFileOf(Version version) {
        return Path.of(version.getSubmission().getStoredPath());
    }

    private Submission storeSubmission(Project project, String fileName, String content) throws IOException {
        Path folder = Files.createTempDirectory("outputhub-naming");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, content);
        return submissions.save(new Submission(project, fileName, file.toString(), "sha-" + fileName,
                LocalDateTime.now()));
    }
}
