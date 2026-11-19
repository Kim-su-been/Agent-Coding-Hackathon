package com.outputhub.service;

import com.outputhub.domain.AuditLog;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.MatchSuggestion;
import com.outputhub.domain.Project;
import com.outputhub.domain.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트별 산출물 목록(기준대장 행) 추가·삭제 기능. */
@Service
public class ChecklistItemService {

    private final Project.Repository projects;
    private final ChecklistItem.Repository items;
    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;
    private final MatchSuggestion.Repository suggestions;
    private final AuditLog.Repository audits;

    public ChecklistItemService(Project.Repository projects, ChecklistItem.Repository items,
                                Deliverable.Repository deliverables, Version.Repository versions,
                                MatchSuggestion.Repository suggestions, AuditLog.Repository audits) {
        this.projects = projects;
        this.items = items;
        this.deliverables = deliverables;
        this.versions = versions;
        this.suggestions = suggestions;
        this.audits = audits;
    }

    /** 산출물 항목을 프로젝트 대장에 추가하고 감사 로그를 남긴다. PMO(주간보고) 단계는 주기 접수 전용이라 추가할 수 없다.
     *  마감일(REQ-outputhub-028)은 선택이고, 넣으면 감사 로그에 함께 기록한다. */
    @Transactional
    public ChecklistItem add(Long projectId, String phase, String itemName, boolean required, LocalDate dueDate) {
        if (ChecklistItem.PMO_PHASE.equals(phase)) {
            throw new IllegalArgumentException("PMO 단계(주간보고)에는 산출물 항목을 추가할 수 없습니다");
        }
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다: " + projectId));
        ChecklistItem created = items.save(new ChecklistItem(project, phase, itemName, required, dueDate));
        String detail = dueDate == null ? phase + " · " + itemName
                : phase + " · " + itemName + " · 마감일 " + dueDate;
        audits.save(new AuditLog(created, "항목 추가", detail, "PMO", LocalDateTime.now()));
        return created;
    }

    /** 산출물 항목을 삭제한다. 연결된 제출 이력(버전·제안·감사)도 함께 정리한다. */
    @Transactional
    public void remove(Long projectId, Long itemId) {
        ChecklistItem item = items.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("산출물 항목을 찾을 수 없습니다: " + itemId));
        if (!item.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("다른 프로젝트의 항목은 삭제할 수 없습니다");
        }
        deleteLinkedData(item);
        items.delete(item);
    }

    private void deleteLinkedData(ChecklistItem item) {
        for (AuditLog audit : audits.findByItemIdOrderByIdAsc(item.getId())) {
            audits.delete(audit);
        }
        for (MatchSuggestion suggestion : suggestions.findAll()) {
            if (item.equals(suggestion.getTargetItem())) {
                suggestions.delete(suggestion);
            }
        }
        deliverables.findByItemId(item.getId()).ifPresent(deliverable -> {
            for (Version version : versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId())) {
                versions.delete(version);
            }
            deliverables.delete(deliverable);
        });
    }

    /** 상세 화면에서 마감일을 수정한다(사용자 요구 2026-11-19). dueDate null 이면 미지정으로 해제하고, 감사 로그를 남긴다. */
    @Transactional
    public ChecklistItem changeDueDate(Long itemId, LocalDate dueDate) {
        ChecklistItem item = items.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("산출물 항목을 찾을 수 없습니다: " + itemId));
        LocalDate previous = item.getDueDate();
        item.changeDueDate(dueDate);
        ChecklistItem saved = items.save(item);
        String detail = "마감일 " + (previous == null ? "미지정" : previous) + " → "
                + (dueDate == null ? "미지정" : dueDate);
        audits.save(new AuditLog(saved, "마감일 변경", detail, "PMO", LocalDateTime.now()));
        return saved;
    }

    /** 프로젝트 대장 행을 단계 순서대로 돌려준다. */
    @Transactional(readOnly = true)
    public List<ChecklistItem> rowsOf(Long projectId) {
        return items.findByProjectIdOrderByIdAsc(projectId);
    }
}
