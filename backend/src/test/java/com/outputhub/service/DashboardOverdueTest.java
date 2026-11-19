package com.outputhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.outputhub.api.ProjectController.DashboardView;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** REQ-outputhub-028: 마감일·지연 판정(사용자 요구 2026-11-19). 기준 날짜를 2026-11-19로 고정해 검증한다. */
@ExtendWith(MockitoExtension.class)
class DashboardOverdueTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    @Mock
    ChecklistItem.Repository items;
    @Mock
    Deliverable.Repository deliverables;

    DashboardService dashboardService;
    Project project;
    List<ChecklistItem> storedItems;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(items, deliverables, null, TODAY);
        project = new Project("지연 판정 검증", "A수행사");
        when(deliverables.findByItemId(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("지연: 마감일이 지났는데 아직 검토완료가 아니면 행과 단계에 지연으로 표시된다")
    void overdueWhenPastDueAndNotConfirmed() {
        storedItems = List.of(new ChecklistItem(project, "착수", "사업수행계획서", true,
                LocalDate.of(2026, 11, 10)));
        stubDashboardRows();

        DashboardView view = dashboardService.load(project);

        assertThat(rowOf(view, "사업수행계획서").overdue()).isTrue();
        assertThat(rowOf(view, "사업수행계획서").dueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(view.phases().get(0).overdueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("지연 아님: 마감일이 지났어도 검토완료면 지연이 아니다")
    void notOverdueWhenConfirmed() {
        ChecklistItem done = new ChecklistItem(project, "착수", "사업수행계획서", true,
                LocalDate.of(2026, 11, 10));
        done.transitionTo(ChecklistItem.Status.SUBMITTED);
        done.transitionTo(ChecklistItem.Status.CONFIRMED);
        storedItems = List.of(done);
        stubDashboardRows();

        DashboardView view = dashboardService.load(project);

        assertThat(rowOf(view, "사업수행계획서").overdue()).isFalse();
        assertThat(view.phases().get(0).overdueCount()).isZero();
    }

    @Test
    @DisplayName("지연 아님: 마감일이 오늘 이후면 아직 지연이 아니다")
    void notOverdueWhenDueInFuture() {
        storedItems = List.of(new ChecklistItem(project, "분석", "요구사항 정의서", true,
                LocalDate.of(2026, 11, 20)));
        stubDashboardRows();

        DashboardView view = dashboardService.load(project);

        assertThat(rowOf(view, "요구사항 정의서").overdue()).isFalse();
        assertThat(view.phases().get(0).overdueCount()).isZero();
    }

    @Test
    @DisplayName("지연 아님: 마감일 미지정 행(주간보고 등 주기 접수)은 지연 판정에서 제외한다")
    void notOverdueWhenDueDateMissing() {
        storedItems = List.of(new ChecklistItem(project, "PMO", "주간보고서", true, null));
        stubDashboardRows();

        DashboardView view = dashboardService.load(project);

        assertThat(rowOf(view, "주간보고서").dueDate()).isNull();
        assertThat(rowOf(view, "주간보고서").overdue()).isFalse();
    }

    /** 대시보드가 읽는 대장 조회와 프로젝트 조회를 저장된 행으로 고정한다. 저장 전 도메인은 id가 null 이므로 any() 로 맞춘다. */
    private void stubDashboardRows() {
        when(items.findByProjectIdOrderByIdAsc(any())).thenReturn(storedItems);
    }

    private com.outputhub.api.ProjectController.RowView rowOf(DashboardView view, String itemName) {
        return view.rows().stream().filter(row -> itemName.equals(row.itemName())).findFirst().orElseThrow();
    }
}
