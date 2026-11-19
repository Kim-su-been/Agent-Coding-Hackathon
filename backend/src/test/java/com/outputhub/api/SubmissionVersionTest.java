package com.outputhub.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.service.VersionRegistrationService;
import com.outputhub.storage.StorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.transaction.annotation.Transactional;

/** 재제출 시 이전 버전 수집(REQ-outputhub-007)과 old 폴더 위치 추적, 주간보고 누적 예외 검증. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Transactional
class SubmissionVersionTest {

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
    @Autowired
    StorageService storage;

    @Test
    @DisplayName("TC-07: 재제출 시 이전 버전은 old 폴더로 수집되고 원본 위치도 함께 갱신된다")
    void resubmitCollectsOldVersionIntoOldFolder() throws IOException {
        Project project = projects.save(new Project("버전 수집 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission v1 = storeSubmission(project, "요구사항 정의서_v1.txt", "v1 본문");
        Submission v2 = storeSubmission(project, "요구사항 정의서_v2.txt", "v2 본문");

        versionRegistration.register(item, v1, "최초 접수");
        versionRegistration.register(item, v2, "재제출 접수");

        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElseThrow();
        Version latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElseThrow();
        Version oldVersion = versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId()).stream()
                .filter(Version::isOld)
                .findFirst()
                .orElseThrow();

        assertThat(latest.getVersionNo()).isEqualTo(11);
        assertThat(latest.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.11_").endsWith(".txt");
        assertThat(oldVersion.getSubmission().getFileName())
                .startsWith("요구사항 정의서_v0.1_").endsWith(".txt");
        Path oldPath = Path.of(oldVersion.getSubmission().getStoredPath());
        assertThat(oldPath.getParent().getFileName().toString()).isEqualTo("old");
        assertThat(Files.exists(oldPath)).isTrue();
        assertThat(Files.readString(oldPath)).isEqualTo("v1 본문");
        assertThat(storage.resolve(latest.getSubmission().getStoredPath())).exists();
    }

    @Test
    @DisplayName("주간보고서(PMO) 재제출 시 이전 파일이 old가 되지 않고 누적 보관된다")
    void weeklyReportAccumulatesFiles() throws IOException {
        Project project = projects.save(new Project("주간보고 누적 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "PMO", "주간보고서", true));
        Submission week1 = storeSubmission(project, "주간보고서_1주차.txt", "1주차 본문");
        Submission week2 = storeSubmission(project, "주간보고서_2주차.txt", "2주차 본문");

        versionRegistration.register(item, week1, "1주차 접수");
        versionRegistration.register(item, week2, "2주차 접수");

        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElseThrow();
        List<Version> all = versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId());

        assertThat(all).hasSize(2);
        assertThat(all).allSatisfy(version -> assertThat(version.isOld()).isFalse());
        assertThat(all.get(1).getSubmission().getFileName())
                .startsWith("주간보고서_v0.1_").endsWith(".txt");
        assertThat(all.get(0).getSubmission().getFileName())
                .startsWith("주간보고서_v0.11_").endsWith(".txt");
        assertThat(storage.resolve(all.get(0).getSubmission().getStoredPath())).exists();
        assertThat(storage.resolve(all.get(1).getSubmission().getStoredPath())).exists();
    }

    private Submission storeSubmission(Project project, String fileName, String content) throws IOException {
        Path folder = Files.createTempDirectory("outputhub-test");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, content);
        return submissions.save(new Submission(project, fileName, file.toString(), "sha-" + fileName,
                LocalDateTime.now()));
    }
}
