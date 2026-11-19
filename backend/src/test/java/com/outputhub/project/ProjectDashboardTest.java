package com.outputhub.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.outputhub.api.DeliverableController;
import com.outputhub.api.ProjectController;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.service.ChecklistItemService;
import com.outputhub.service.DashboardService;
import com.outputhub.service.VersionRegistrationService;
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

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class ProjectDashboardTest {

    @Autowired
    Project.Repository projects;
    @Autowired
    ChecklistItem.Repository items;
    @Autowired
    ChecklistItemService itemService;
    @Autowired
    DashboardService dashboardService;
    @Autowired
    Deliverable.Repository deliverables;
    @Autowired
    Version.Repository versions;
    @Autowired
    Submission.Repository submissions;
    @Autowired
    VersionRegistrationService versionRegistration;
    @Autowired
    DeliverableController deliverableController;

    @Test
    @DisplayName("기본 세팅: 프로젝트가 없으면 AI.NSP·AI.NP·e-HR 3개가 단계별 목록으로 등록된다")
    void seedDefaultProjects() {
        List<Project> seeded = projects.findAll().stream()
                .filter(p -> List.of("AI.NSP", "AI.NP", "e-HR").contains(p.getName()))
                .toList();

        assertThat(seeded).hasSize(3);
        for (Project project : seeded) {
            List<ChecklistItem> rows = items.findByProjectIdOrderByIdAsc(project.getId());
            assertThat(rows).hasSize(23);
            assertThat(rows.get(0).getPhase()).isEqualTo("착수");
            assertThat(rows.get(rows.size() - 1).getPhase()).isEqualTo("PMO");
        }
    }

    @Test
    @DisplayName("대시보드: 전체·단계별 완료율은 모두 검토완료 기준이다(사용자 요구 2026-11-19)")
    void dashboardProgress() {
        Project project = projects.save(new Project("대시보드 검증", "A수행사"));
        ChecklistItem confirmed = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        confirmed.transitionTo(ChecklistItem.Status.SUBMITTED);
        confirmed.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(confirmed);
        ChecklistItem reviewNeeded = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        reviewNeeded.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(reviewNeeded);

        ProjectController.DashboardView view = dashboardService.load(project);

        assertThat(view.overallPct()).isEqualTo(50);
        assertThat(view.confirmedCount()).isEqualTo(1);
        assertThat(view.phases()).extracting(ProjectController.PhaseView::name)
                .containsExactly("착수", "분석");
        assertThat(view.phases().get(0).confirmed()).isEqualTo(1);
        assertThat(view.phases().get(0).submittedPct()).isEqualTo(100);
        assertThat(view.phases().get(0).pct()).isEqualTo(100);
        assertThat(view.phases().get(1).submitted()).isEqualTo(1);
        assertThat(view.phases().get(1).confirmed()).isZero();
        assertThat(view.phases().get(1).submittedPct()).isEqualTo(100);
        assertThat(view.phases().get(1).pct()).isZero();
        assertThat(view.badges()).containsExactly(
                "미제출 0종", "검토 필요 1종", "보완 필요 0종", "검토완료 1종");
    }

    @Test
    @DisplayName("대시보드: 산출물이 없으면 상태 배지가 모두 0종으로 표시된다")
    void emptyProjectBadges() {
        Project project = projects.save(new Project("빈 대시보드 검증", "A수행사"));

        ProjectController.DashboardView view = dashboardService.load(project);

        assertThat(view.overallPct()).isZero();
        assertThat(view.confirmedCount()).isZero();
        assertThat(view.phases()).isEmpty();
        assertThat(view.badges()).containsExactly(
                "미제출 0종", "검토 필요 0종", "보완 필요 0종", "검토완료 0종");
    }

    @Test
    @DisplayName("목록 관리: 산출물 항목을 추가하면 대시보드 목록에 반영된다")
    void addItem() {
        Project project = projects.save(new Project("항목 추가 검증", "A수행사"));

        ChecklistItem added = itemService.add(project.getId(), "착수", "품의서", false, null);

        assertThat(added.getId()).isNotNull();
        assertThat(added.getPhase()).isEqualTo("착수");
        assertThat(items.findByProjectIdOrderByIdAsc(project.getId()))
                .extracting(ChecklistItem::getItemName)
                .contains("품의서");
    }

    @Test
    @DisplayName("목록 관리: PMO(주간보고) 단계에는 산출물 항목을 추가할 수 없다")
    void addItemRejectsPmoPhase() {
        Project project = projects.save(new Project("PMO 추가 제한 검증", "A수행사"));

        assertThatThrownBy(() -> itemService.add(project.getId(), "PMO", "품의서", false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PMO");
    }

    @Test
    @DisplayName("대시보드: PMO 주간보고는 통계에서 제외되고 행에는 최신본 파일 1개만 내려온다(REQ-outputhub-023, AC-023-1)")
    void weeklyReportExcludedFromStatsWithLatestFileOnly() throws IOException {
        Project project = projects.save(new Project("주간보고 통계 제외 검증", "A수행사"));
        ChecklistItem plan = items.save(new ChecklistItem(project, "착수", "사업수행계획서", true));
        plan.transitionTo(ChecklistItem.Status.SUBMITTED);
        plan.transitionTo(ChecklistItem.Status.CONFIRMED);
        items.save(plan);
        ChecklistItem weekly = items.save(new ChecklistItem(project, "PMO", "주간보고서", true));
        registerWeeklyVersion(weekly, "주간보고서_1주차.txt", "1주차 본문");
        registerWeeklyVersion(weekly, "주간보고서_2주차.txt", "2주차 본문");

        ProjectController.DashboardView view = dashboardService.load(project);

        assertThat(view.phases()).extracting(ProjectController.PhaseView::name).containsExactly("착수");
        assertThat(view.totalCount()).isEqualTo(1);
        assertThat(view.overallPct()).isEqualTo(100);
        ProjectController.RowView weeklyRow = view.rows().stream()
                .filter(row -> row.itemName().equals("주간보고서"))
                .findFirst()
                .orElseThrow();
        assertThat(weeklyRow.latestFileName()).isEqualTo("주간보고서_2주차.txt");
        assertThat(weeklyRow.latestDownloadUrl()).startsWith("/api/versions/");
        assertThat(weeklyRow.latestVersionNo()).isEqualTo(11);
        assertThat(weeklyRow.latestVersionLabel()).isEqualTo("0.11");
    }

    @Test
    @DisplayName("상세 조회: 업로드된 모든 버전(old 포함)을 최신순으로 반환한다(REQ-outputhub-024, AC-024-1)")
    void detailReturnsAllVersionsIncludingOld() throws IOException {
        Project project = projects.save(new Project("파일 버전 내역 검증", "A수행사"));
        ChecklistItem item = items.save(new ChecklistItem(project, "분석", "요구사항 정의서", true));
        Submission first = storeSubmission(project, "요구사항 정의서_v1.txt", "v1 본문");
        Submission second = storeSubmission(project, "요구사항 정의서_v2.txt", "v2 본문");
        versionRegistration.register(item, first, "최초 접수");
        versionRegistration.register(item, second, "재제출 접수");

        DeliverableController.ItemDetail detail = deliverableController.detail(item.getId()).getBody();

        assertThat(detail).isNotNull();
        assertThat(detail.versions()).hasSize(2);
        assertThat(detail.versions().get(0).versionNo()).isEqualTo(11);
        assertThat(detail.versions().get(0).old()).isFalse();
        assertThat(detail.versions().get(1).versionNo()).isEqualTo(10);
        assertThat(detail.versions().get(1).old()).isTrue();
        assertThat(detail.versions()).allSatisfy(version -> {
            assertThat(version.fileName()).isNotBlank();
            assertThat(version.downloadUrl()).startsWith("/api/versions/");
        });
    }

    private Submission storeSubmission(Project project, String fileName, String content) throws IOException {
        // SubmissionVersionTest와 같이 제출마다 별도 임시 폴더를 쓴다: 파일명 규칙 변경이 같은 폴더의 다른 제출과 충돌하지 않게
        Path folder = Files.createTempDirectory("outputhub-test");
        Path file = Files.createTempFile(folder, "submission-", ".txt");
        Files.writeString(file, content);
        return submissions.save(new Submission(project, fileName, file.toString(), "sha-" + fileName,
                LocalDateTime.now()));
    }

    private void registerWeeklyVersion(ChecklistItem item, String fileName, String content)
            throws IOException {
        Path file = Files.createTempFile("outputhub-test-", ".txt");
        Files.writeString(file, content);
        Submission submission = submissions.save(new Submission(item.getProject(), fileName, file.toString(),
                "sha-" + fileName, LocalDateTime.now()));
        Deliverable deliverable = deliverables.findByItemId(item.getId())
                .orElseGet(() -> deliverables.save(new Deliverable(item)));
        int versionNo = deliverable.nextVersionNo();
        deliverables.save(deliverable);
        versions.save(new Version(deliverable, versionNo, submission));
    }

    @Test
    @DisplayName("목록 관리: 산출물 항목을 삭제하면 연결된 제출 이력도 함께 정리된다")
    void removeItem() {
        Project project = projects.save(new Project("항목 삭제 검증", "A수행사"));
        ChecklistItem added = itemService.add(project.getId(), "착수", "임시 산출물", false, null);

        itemService.remove(project.getId(), added.getId());

        assertThat(items.findById(added.getId())).isEmpty();
        assertThatThrownBy(() -> itemService.remove(project.getId(), 999_999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
