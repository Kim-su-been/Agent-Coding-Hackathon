package com.outputhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.outputhub.domain.AuditLog;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
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

/** REQ-outputhub-022: 검토완료 기록의 파일명 규칙 정합화(시작 점검) 검증. */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@Transactional
class ConfirmedFileNamingReconcilerTest {

    private static final String LEGACY_FILE_NAME = "요구사항 정의서_v2.txt";
    private static final String RULE_FILE_NAME = "요구사항 정의서_v1.0.txt";
    private static final String RECONCILE_ACTION = "파일명 정합화";

    @Autowired
    ConfirmedFileNamingReconciler reconciler;
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
    AuditLog.Repository audits;

    @Test
    @DisplayName("AC-022-3: 낡은 기록(확정 시점 파일명 변경 없음)을 검토완료 규칙 파일명으로 바로잡는다")
    void reconcileLegacyConfirmedRecord() throws IOException {
        // Given: 낡은 빌드에서 확정된 것처럼 검토완료 상태에 원본 파일명("요구사항 정의서_v2.txt")이 남은 기록
        Project project = projects.save(new Project("정합화 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission submission = storeSubmission(project, LEGACY_FILE_NAME, "정의서 본문");
        versionRegistration.register(item, submission, "최초 접수");
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        item.transitionTo(ChecklistItem.Status.CONFIRMED);
        rewriteSubmissionFileToLegacyName(submission, LEGACY_FILE_NAME);

        // When: 검토완료 항목의 파일명 정합화를 수행한다
        List<String> healed = reconciler.reconcileConfirmedItems();

        // Then: 최신본 파일명이 {항목}_v1.0.txt 로 바뀌고 실제 파일·감사 로그·버전 카운터가 함께 맞춰진다
        assertThat(healed).containsExactly(RULE_FILE_NAME);
        Version latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(
                deliverables.findByItemId(item.getId()).orElseThrow().getId()).orElseThrow();
        assertThat(latest.getSubmission().getFileName()).isEqualTo(RULE_FILE_NAME);
        assertThat(Path.of(latest.getSubmission().getStoredPath()).getFileName().toString())
                .isEqualTo(RULE_FILE_NAME);
        assertThat(Files.readString(Path.of(latest.getSubmission().getStoredPath())))
                .isEqualTo("정의서 본문");
        assertThat(deliverables.findByItemId(item.getId()).orElseThrow().getLatestVersionNo())
                .isEqualTo(Deliverable.CONFIRMED_VERSION_NO);
        assertThat(audits.findByItemIdOrderByIdAsc(item.getId()))
                .anySatisfy(audit -> {
                    assertThat(audit.getAction()).isEqualTo(RECONCILE_ACTION);
                    assertThat(audit.getDetail())
                            .contains(LEGACY_FILE_NAME + " → " + RULE_FILE_NAME);
                });
    }

    @Test
    @DisplayName("AC-022-3: 파일명은 이미 규칙대로인 낡은 기록은 그대로 두고 재수락 카운터만 맞춘다")
    void keepConformingNameAndFixCounterOnly() throws IOException {
        // Given: 파일명은 규칙대로지만 카운터가 v1.0 미만인 낡은 검토완료 기록 1건
        Project project = projects.save(new Project("정합화 유지 검증", "A수행사"));
        ChecklistItem confirmed = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        Submission confirmedFile = storeSubmission(project, "사업수행계획서.txt", "계획 본문");
        versionRegistration.register(confirmed, confirmedFile, "최초 접수");
        confirmed.transitionTo(ChecklistItem.Status.SUBMITTED);
        confirmed.transitionTo(ChecklistItem.Status.CONFIRMED);
        rewriteSubmissionFileToLegacyName(confirmedFile, "사업수행계획서_v1.0.txt");

        // When: 검토완료 항목의 파일명 정합화를 수행한다
        List<String> healed = reconciler.reconcileConfirmedItems();

        // Then: 파일명과 감사 로그는 그대로고, 카운터만 v1.0(100)으로 맞춰져 재수락이 v1.01부터 시작한다
        assertThat(healed).isEmpty();
        assertThat(versionRegistration.latestSubmissionFileName(confirmed))
                .isEqualTo("사업수행계획서_v1.0.txt");
        assertThat(deliverables.findByItemId(confirmed.getId()).orElseThrow().getLatestVersionNo())
                .isEqualTo(Deliverable.CONFIRMED_VERSION_NO);
        assertThat(audits.findByItemIdOrderByIdAsc(confirmed.getId()))
                .noneSatisfy(audit -> assertThat(audit.getAction()).isEqualTo(RECONCILE_ACTION));
    }

    @Test
    @DisplayName("AC-022-3: 규칙에서 벗어난 파일명을 가진 검토 필요 기록은 정합화하지 않는다")
    void leaveUnconfirmedRecordsAlone() throws IOException {
        // Given: 낡은 파일명을 가진 검토 필요 기록 1건
        Project project = projects.save(new Project("정합화 대상외 검증", "A수행사"));
        ChecklistItem underReview = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission reviewFile = storeSubmission(project, LEGACY_FILE_NAME, "정의서 본문");
        versionRegistration.register(underReview, reviewFile, "최초 접수");
        underReview.transitionTo(ChecklistItem.Status.SUBMITTED);

        // When: 검토완료 항목의 파일명 정합화를 수행한다
        List<String> healed = reconciler.reconcileConfirmedItems();

        // Then: 검토 필요 기록의 파일명은 그대로다
        assertThat(healed).isEmpty();
        assertThat(versionRegistration.latestSubmissionFileName(underReview))
                .startsWith("요구사항 정의서_v0.1_");
    }

    @Test
    @DisplayName("AC-022-3: 정합화 대상 파일이 저장소에 없어도 시작 점검이 중단되지 않는다")
    void skipMissingFileWithoutStopping() throws IOException {
        // Given: 원본 파일이 사라진 검토완료 기록 1건과, 정상적인 낡은 파일명 기록 1건
        Project project = projects.save(new Project("정합화 예외 검증", "A수행사"));
        ChecklistItem broken = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission missingFile = storeSubmission(project, LEGACY_FILE_NAME, "사라진 본문");
        versionRegistration.register(broken, missingFile, "최초 접수");
        broken.transitionTo(ChecklistItem.Status.SUBMITTED);
        broken.transitionTo(ChecklistItem.Status.CONFIRMED);
        Files.delete(Path.of(missingFile.getStoredPath()));

        ChecklistItem healthy = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        Submission presentFile = storeSubmission(project, "사업수행계획서_v2.txt", "계획 본문");
        versionRegistration.register(healthy, presentFile, "최초 접수");
        healthy.transitionTo(ChecklistItem.Status.SUBMITTED);
        healthy.transitionTo(ChecklistItem.Status.CONFIRMED);
        rewriteSubmissionFileToLegacyName(presentFile, "사업수행계획서_v2.txt");

        // When: 검토완료 항목의 파일명 정합화를 수행한다
        List<String> healed = reconciler.reconcileConfirmedItems();

        // Then: 파일이 없는 기록은 건너뛰고 나머지 기록은 규칙 파일명으로 바로잡는다
        assertThat(healed).containsExactly("사업수행계획서_v1.0.txt");
        assertThat(versionRegistration.latestSubmissionFileName(broken))
                .startsWith("요구사항 정의서_v0.1_");
    }

    private Submission storeSubmission(Project project, String fileName, String content) throws IOException {
        Path folder = Files.createTempDirectory("outputhub-reconcile");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, content);
        return submissions.save(new Submission(project, fileName, file.toString(), "sha-" + fileName,
                LocalDateTime.now()));
    }

    private void rewriteSubmissionFileToLegacyName(Submission submission, String legacyName) throws IOException {
        Path current = Path.of(submission.getStoredPath());
        Path legacy = current.getParent().resolve(legacyName);
        Files.move(current, legacy);
        submission.renameTo(legacyName, legacy.toString());
    }
}
