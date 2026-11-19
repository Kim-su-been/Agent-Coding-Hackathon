package com.outputhub.api;

import com.outputhub.domain.AuditLog;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.MatchSuggestion;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import com.outputhub.service.VersionRegistrationService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeliverableController {

    public record ItemDetail(Long itemId, String phase, String itemName, boolean required, String status,
                             String supplementReason, List<VersionView> versions, List<AuditView> audits) {
    }

    public record VersionView(Long versionId, int versionNo, String versionLabel, String fileName, boolean old,
                              String uploadedAt, String downloadUrl) {
    }

    public record AuditView(Long id, String action, String detail, String actor, String at) {
    }

    public record SupplementRequest(String reason) {
    }

    /** 상태 변경 응답(JSON): 라벨 문자열을 그대로 내보내면 프론트 파싱이 깨진다. */
    public record StatusResponse(String status) {
    }

    private final ChecklistItem.Repository items;
    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;
    private final MatchSuggestion.Repository suggestions;
    private final AuditLog.Repository audits;
    private final VersionRegistrationService versionRegistration;

    public DeliverableController(ChecklistItem.Repository items, Deliverable.Repository deliverables,
                                 Version.Repository versions, MatchSuggestion.Repository suggestions,
                                 AuditLog.Repository audits, VersionRegistrationService versionRegistration) {
        this.items = items;
        this.deliverables = deliverables;
        this.versions = versions;
        this.suggestions = suggestions;
        this.audits = audits;
        this.versionRegistration = versionRegistration;
    }

    @Transactional(readOnly = true)
    @GetMapping("/api/deliverables/{itemId}")
    public ResponseEntity<ItemDetail> detail(@PathVariable Long itemId) {
        ChecklistItem item = items.findById(itemId).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        List<VersionView> versionViews = new java.util.ArrayList<>();
        var deliverable = deliverables.findByItemId(itemId).orElse(null);
        if (deliverable != null) {
            for (Version version : versions.findByDeliverableIdOrderByVersionNoDesc(deliverable.getId())) {
                versionViews.add(new VersionView(version.getId(), version.getVersionNo(),
                        VersionNaming.format(version.getVersionNo()), version.getSubmission().getFileName(),
                        version.isOld(), version.getSubmission().getUploadedAt().toString(),
                        "/api/versions/" + version.getId() + "/download"));
            }
        }
        List<AuditView> auditViews = audits.findByItemIdOrderByIdAsc(itemId).stream()
                .map(a -> new AuditView(a.getId(), a.getAction(), a.getDetail(), a.getActor(), a.getAt().toString()))
                .toList();
        return ResponseEntity.ok(new ItemDetail(itemId, item.getPhase(), item.getItemName(), item.isRequired(),
                item.getStatus().label(), item.getSupplementReason(), versionViews, auditViews));
    }

    /** REQ-outputhub-010: 확정은 접수 상태에서만 가능하다. REQ-outputhub-022: 확정 시 최신본 파일명을 v1.0으로 바꾼다. */
    @Transactional
    @PostMapping("/api/deliverables/{itemId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable Long itemId) {
        ChecklistItem item = items.findById(itemId).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            item.transitionTo(ChecklistItem.Status.CONFIRMED);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
        items.save(item);
        String confirmedFileName = versionRegistration.renameLatestToConfirmed(item);
        String detail = confirmedFileName == null ? "상태 확정" : "상태 확정 — 최신본 파일명 " + confirmedFileName;
        audits.save(new AuditLog(item, "확정", detail, "PMO", LocalDateTime.now()));
        return ResponseEntity.ok(new StatusResponse(item.getStatus().label()));
    }

    /** REQ-outputhub-009: 보완 필요 지정(사유 필수, AC-009-1). */
    @Transactional
    @PostMapping("/api/deliverables/{itemId}/request-supplement")
    public ResponseEntity<?> requestSupplement(@PathVariable Long itemId, @RequestBody SupplementRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body("보완 사유를 입력하세요");
        }
        ChecklistItem item = items.findById(itemId).orElse(null);
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            item.requestSupplement(request.reason());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
        items.save(item);
        audits.save(new AuditLog(item, "보완 필요", request.reason(), "PMO", LocalDateTime.now()));
        return ResponseEntity.ok(new StatusResponse(item.getStatus().label()));
    }

    /** REQ-outputhub-008: 검토완료 항목 재업로드 승인 — 상태를 검토 필요(검토전)로 되돌리고 새 버전으로 등록한다(AC-008-1). */
    @Transactional
    @PostMapping("/api/deliverables/{itemId}/replace-confirmed")
    public ResponseEntity<?> replaceConfirmed(@PathVariable Long itemId, @RequestBody SupplementRequest request) {
        Long suggestionId = request == null ? null : parseLong(request.reason());
        if (suggestionId == null) {
            return ResponseEntity.badRequest().body("교체할 제안 번호가 필요합니다");
        }
        MatchSuggestion suggestion = suggestions.findById(suggestionId).orElse(null);
        ChecklistItem item = items.findById(itemId).orElse(null);
        if (suggestion == null || item == null
                || (suggestion.getVerdict() != MatchSuggestion.Verdict.LINK
                    && suggestion.getVerdict() != MatchSuggestion.Verdict.NEW)
                || suggestion.getStatus() != MatchSuggestion.Status.PENDING) {
            return ResponseEntity.badRequest().body("교체할 수락 대기 제안이 없습니다");
        }
        if (!item.getProject().getId().equals(suggestion.getProject().getId())) {
            return ResponseEntity.badRequest().body("다른 프로젝트의 산출물 항목입니다");
        }
        if (item.getStatus() != ChecklistItem.Status.CONFIRMED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("검토완료 항목만 재업로드 승인 대상입니다");
        }
        if (versionRegistration.isSameHashAlreadyRegistered(item.getProject(), suggestion.getSubmission())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("동일 해시 파일이 이미 등록되어 있습니다(REQ-outputhub-006 중복)");
        }
        item.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(item);
        versionRegistration.register(item, suggestion.getSubmission(), suggestion.getEvidence());
        suggestion.accept();
        suggestions.save(suggestion);
        String preserved = item.accumulatesFiles() ? "이전 검토완료본은 누적 보관"
                : "이전 검토완료본은 old로 보존";
        audits.save(new AuditLog(item, "재업로드 승인", suggestion.getSubmission().getFileName()
                + " — 검토 필요(검토전)로 전환, " + preserved,
                "PMO", LocalDateTime.now()));
        return ResponseEntity.ok(new StatusResponse(item.getStatus().label()));
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
