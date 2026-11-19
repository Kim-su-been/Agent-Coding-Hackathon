package com.outputhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outputhub.storage.StorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class DeliverableStateTest {

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
    @Autowired
    StorageService storage;

    @Test
    @DisplayName("TC-01: 기준대장 등록으로 산출물 행이 미완료 상태로 만들어진다")
    void registerChecklist() {
        Project project = projects.save(new Project("테스트 프로젝트", "A수행사"));
        items.save(new ChecklistItem(project, "착수", "견적서", true));
        items.save(new ChecklistItem(project, "착수", "인력투입계획서", true));

        var rows = items.findByProjectIdOrderByIdAsc(project.getId());

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(ChecklistItem.Status.NOT_SUBMITTED);
            assertThat(row.isRequired()).isTrue();
            assertThat(row.getPhase()).isEqualTo("착수");
        });
    }

    @Test
    @DisplayName("L-outputhub-006: 허용되지 않은 상태 전환(미완료→검토완료)은 거부된다")
    void rejectIllegalTransition() {
        ChecklistItem row = items.save(new ChecklistItem(roundOf(), "착수", "견적서", true));

        assertThatThrownBy(() -> row.transitionTo(ChecklistItem.Status.CONFIRMED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-06·TC-07: 검토 필요→검토완료→보완 필요 지정→재접수 복귀가 동작한다")
    void supplementFlow() {
        ChecklistItem row = items.save(new ChecklistItem(roundOf(), "착수", "사업수행계획서", true));
        row.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(row);
        row.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(row);
        assertThat(row.getStatus()).isEqualTo(ChecklistItem.Status.CONFIRMED);

        row.transitionTo(ChecklistItem.Status.SUBMITTED);
        row.requestSupplement("투입 인력 산근 누락");
        items.save(row);
        assertThat(row.getStatus()).isEqualTo(ChecklistItem.Status.SUPPLEMENT_REQUIRED);
        assertThat(row.getSupplementReason()).isEqualTo("투입 인력 산근 누락");

        row.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(row);
        assertThat(row.getStatus()).isEqualTo(ChecklistItem.Status.SUBMITTED);
        assertThat(row.getSupplementReason()).isNull();
    }

    @Test
    @DisplayName("상태 라벨은 대시보드 용어(미완료·검토 필요·검토완료·보완 필요)를 쓴다")
    void statusLabels() {
        assertThat(ChecklistItem.Status.NOT_SUBMITTED.label()).isEqualTo("미완료");
        assertThat(ChecklistItem.Status.SUBMITTED.label()).isEqualTo("검토 필요");
        assertThat(ChecklistItem.Status.CONFIRMED.label()).isEqualTo("검토완료");
        assertThat(ChecklistItem.Status.SUPPLEMENT_REQUIRED.label()).isEqualTo("보완 필요");
    }

    @Test
    @DisplayName("REQ-outputhub-007: 재제출 시 버전이 갱신되고 이전 버전은 old로 수집된다")
    void versionUpdateCollectsOld() throws Exception {
        ChecklistItem row = items.save(new ChecklistItem(roundOf(), "착수", "WBS", true));
        Deliverable deliverable = deliverables.save(new Deliverable(row));

        Path first = Files.createTempFile("wbs-v1", ".xlsx");
        Version v1 = versions.save(new Version(deliverable, deliverable.nextVersionNo(),
                submissions.save(new Submission(roundOf(), "WBS_v1.xlsx", first.toString(), "hash-1", java.time.LocalDateTime.now()))));
        Path second = Files.createTempFile("wbs-v2", ".xlsx");
        v1.markOld();
        versions.save(v1);
        Version v2 = versions.save(new Version(deliverable, deliverable.nextVersionNo(),
                submissions.save(new Submission(roundOf(), "WBS_v2.xlsx", second.toString(), "hash-2", java.time.LocalDateTime.now()))));

        assertThat(v2.getVersionNo()).isEqualTo(11);
        assertThat(v1.isOld()).isTrue();
        assertThat(versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId()))
                .hasValueSatisfying(v -> assertThat(v.getVersionNo()).isEqualTo(11));
    }

    @Test
    @DisplayName("TC-09·AC-002-1: 5종 중 4종 제출이면 제출 완료율 80%다")
    void aggregateProgress() {
        Project project = projects.save(new Project("완료율 검증", "A수행사"));
        ChecklistItem confirmed = items.save(new ChecklistItem(project, "착수", "견적서", true));
        confirmed.transitionTo(ChecklistItem.Status.SUBMITTED);
        confirmed.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(confirmed);
        ChecklistItem received = items.save(new ChecklistItem(project, "착수", "WBS", true));
        received.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(received);
        ChecklistItem supplement = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        supplement.transitionTo(ChecklistItem.Status.SUBMITTED);
        supplement.requestSupplement("사유");
        items.save(supplement);
        items.save(new ChecklistItem(project, "착수", "인력투입계획서", true));
        ChecklistItem extra = items.save(new ChecklistItem(project, "착수", "기술협상리스트", true));
        extra.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(extra);

        var rows = items.findByProjectIdOrderByIdAsc(project.getId());
        long total = rows.size();
        long receivedCount = rows.stream().filter(ChecklistItem::isReceived).count();

        assertThat(total).isEqualTo(5);
        assertThat(receivedCount).isEqualTo(4);
        assertThat((int) Math.round(receivedCount * 100.0 / total)).isEqualTo(80);
    }

    @Test
    @DisplayName("REQ-outputhub-006: 동일 해시 판단 기준은 SHA-256이고 값이 같으면 중복으로 본다")
    void hashIdentity() throws Exception {
        Path a = Files.createTempFile("same-a", ".pdf");
        Path b = Files.createTempFile("same-b", ".pdf");
        Files.writeString(a, "동일 내용");
        Files.writeString(b, "동일 내용");

        assertThat(StorageService.sha256(a)).isEqualTo(StorageService.sha256(b));
        Files.writeString(a, "다른 내용");
        assertThat(StorageService.sha256(a)).isNotEqualTo(StorageService.sha256(b));
    }

    private Project roundOf() {
        return projects.save(new Project("상태 흐름 검증", "A수행사"));
    }
}
