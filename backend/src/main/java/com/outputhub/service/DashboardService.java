package com.outputhub.service;

import com.outputhub.api.ProjectController.DashboardView;
import com.outputhub.api.ProjectController.PhaseView;
import com.outputhub.api.ProjectController.RowView;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드 집계(REQ-outputhub-006·016): 전체·단계별 완료율을 모두 검토완료 기준으로 계산한다(사용자 요구 2026-11-19). */
@Service
public class DashboardService {

    private final ChecklistItem.Repository items;
    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;

    public DashboardService(ChecklistItem.Repository items, Deliverable.Repository deliverables,
                            Version.Repository versions) {
        this.items = items;
        this.deliverables = deliverables;
        this.versions = versions;
    }

    @Transactional(readOnly = true)
    public DashboardView load(Project project) {
        List<ChecklistItem> rows = items.findByProjectIdOrderByIdAsc(project.getId());
        List<ChecklistItem> statRows = rows.stream().filter(row -> !row.accumulatesFiles()).toList();
        // 전체 완료율도 단계별과 같은 검토완료 기준으로 계산해 그래프와 ‘전체 N종 중 K종 완료’ 문구가 일치하게 한다
        int confirmedCount = (int) countStatus(statRows, ChecklistItem.Status.CONFIRMED);
        int overallPct = percentage(confirmedCount, statRows.size());
        return new DashboardView(project, overallPct, confirmedCount, statRows.size(), phaseViews(statRows),
                badges(statRows), rowViews(rows));
    }

    private List<PhaseView> phaseViews(List<ChecklistItem> rows) {
        Map<String, List<ChecklistItem>> grouped = groupByPhase(rows);
        List<PhaseView> phases = new ArrayList<>();
        for (Map.Entry<String, List<ChecklistItem>> entry : grouped.entrySet()) {
            List<ChecklistItem> phaseRows = entry.getValue();
            long submitted = phaseRows.stream().filter(ChecklistItem::isReceived).count();
            long confirmed = countStatus(phaseRows, ChecklistItem.Status.CONFIRMED);
            phases.add(new PhaseView(entry.getKey(), phaseRows.size(), (int) submitted, (int) confirmed,
                    percentage(confirmed, phaseRows.size()), percentage(submitted, phaseRows.size())));
        }
        return phases;
    }

    private long countStatus(List<ChecklistItem> rows, ChecklistItem.Status status) {
        return rows.stream().filter(row -> row.getStatus() == status).count();
    }

    /** 상태 배지(사용자 요구 2026-11-19): 미제출·검토 필요·보완 필요·검토완료 건수를 항상 4개로 표시한다. */
    private List<String> badges(List<ChecklistItem> rows) {
        return List.of(
                badge(rows, ChecklistItem.Status.NOT_SUBMITTED, "미제출"),
                badge(rows, ChecklistItem.Status.SUBMITTED, "검토 필요"),
                badge(rows, ChecklistItem.Status.SUPPLEMENT_REQUIRED, "보완 필요"),
                badge(rows, ChecklistItem.Status.CONFIRMED, "검토완료"));
    }

    private String badge(List<ChecklistItem> rows, ChecklistItem.Status status, String label) {
        return label + " " + countStatus(rows, status) + "종";
    }

    private List<RowView> rowViews(List<ChecklistItem> rows) {
        List<RowView> views = new ArrayList<>();
        for (ChecklistItem row : rows) {
            views.add(toRowView(row));
        }
        return views;
    }

    /** REQ-outputhub-023: 항목별 최신본 1개만 내려준다. 주간보고 누적 항목도 예외가 없다(전체 버전은 상세 화면 REQ-outputhub-024). */
    private RowView toRowView(ChecklistItem row) {
        String fileName = null;
        int versionNo = 0;
        String downloadUrl = null;
        var deliverable = deliverables.findByItemId(row.getId()).orElse(null);
        if (deliverable != null) {
            versionNo = deliverable.getLatestVersionNo();
            var latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                    .orElse(null);
            if (latest != null) {
                fileName = latest.getSubmission().getFileName();
                downloadUrl = "/api/versions/" + latest.getId() + "/download";
            }
        }
        return new RowView(row.getId(), row.getPhase(), row.getItemName(), row.isRequired(),
                row.getStatus().label(), row.getSupplementReason(), versionNo,
                versionNo > 0 ? VersionNaming.format(versionNo) : null,
                fileName, downloadUrl, null);
    }

    private Map<String, List<ChecklistItem>> groupByPhase(List<ChecklistItem> rows) {
        Map<String, List<ChecklistItem>> grouped = new LinkedHashMap<>();
        for (ChecklistItem row : rows) {
            grouped.computeIfAbsent(row.getPhase(), phase -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private int percentage(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }
}
