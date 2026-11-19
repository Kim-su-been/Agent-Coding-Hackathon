package com.outputhub.api;

import com.outputhub.domain.AuditLog;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.MatchSuggestion;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import com.outputhub.match.MatchService;
import com.outputhub.service.VersionRegistrationService;
import com.outputhub.storage.StorageService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SubmissionController {

    /** 신규(자동등록) 승인으로 만들어지는 항목의 단계 */
    static final String AUTO_PHASE = "기타";

    /** REQ-outputhub-021: 업로드 시 항목을 직접 선택했을 때 매칭 근거로 표시한다. */
    static final String MANUAL_TARGET_EVIDENCE = "업로드 시 산출물 항목을 직접 선택했습니다(드롭다운)";

    /** REQ-outputhub-023: 검토대기 카드 드롭다운에서 행을 바꿔 수락했을 때 근거에 덧붙인다. */
    static final String MANUAL_PICK_EVIDENCE = "검토대기에서 산출물 항목을 직접 선택했습니다(드롭다운)";

    /** REQ-outputhub-006: 동일 해시·다른 파일명 접수의 중복 안내 문구. 제안 카드로 저장하지 않는다(사용자 요구 2026-11-19). */
    static final String DUPLICATE_EVIDENCE = "동일 해시 파일이 이미 접수되어 있습니다(집계 제외)";

    public record SuggestionView(Long suggestionId, String verdict, String fileName, String evidence,
                                 List<CandidateView> candidates) {
    }

    /** itemStatus 는 대장 행의 현재 검토 상태(REQ-outputhub-008 팝업 판단용). */
    public record CandidateView(Long itemId, String itemName, int score, String evidence, String itemStatus) {
    }

    private final Project.Repository projects;
    private final ChecklistItem.Repository items;
    private final Submission.Repository submissions;
    private final MatchSuggestion.Repository suggestions;
    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;
    private final AuditLog.Repository audits;
    private final StorageService storage;
    private final MatchService matchService;
    private final VersionRegistrationService versionRegistration;

    public SubmissionController(Project.Repository projects, ChecklistItem.Repository items,
                                Submission.Repository submissions, MatchSuggestion.Repository suggestions,
                                Deliverable.Repository deliverables, Version.Repository versions,
                                AuditLog.Repository audits, StorageService storage,
                                MatchService matchService, VersionRegistrationService versionRegistration) {
        this.projects = projects;
        this.items = items;
        this.submissions = submissions;
        this.suggestions = suggestions;
        this.deliverables = deliverables;
        this.versions = versions;
        this.audits = audits;
        this.storage = storage;
        this.matchService = matchService;
        this.versionRegistration = versionRegistration;
    }

    @Transactional
    @PostMapping("/api/projects/{id}/submissions")
    public ResponseEntity<List<SuggestionView>> upload(@PathVariable Long id,
                                                       @RequestParam("files") List<MultipartFile> files,
                                                       @RequestParam(value = "targetItemId", required = false)
                                                       Long targetItemId) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Project project = projects.findById(id).orElse(null);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        if (targetItemId != null && findProjectItem(project, targetItemId) == null) {
            return ResponseEntity.badRequest().body(List.of());
        }
        List<SuggestionView> result = new ArrayList<>();
        for (MultipartFile file : files) {
            result.add(judge(project, file, targetItemId));
        }
        return ResponseEntity.ok(result);
    }

    /** REQ-outputhub-021: 드롭다운으로 선택한 항목이 이 프로젝트 대장에 있는지 확인한다. */
    private ChecklistItem findProjectItem(Project project, Long targetItemId) {
        ChecklistItem target = items.findById(targetItemId).orElse(null);
        if (target == null || !target.getProject().getId().equals(project.getId())) {
            return null;
        }
        return target;
    }

    private SuggestionView judge(Project project, MultipartFile file, Long targetItemId) {
        StorageService.StoredFile stored = storage.store(project.getName(), file);
        // REQ-outputhub-006: 파일명과 관계없이, 이 프로젝트에 등록된 버전과 해시(내용)가 같으면 중복으로 처리한다
        // (사용자 요구 2026-11-19). 내용이 바뀐 경우에만 새 파일로 인식해 뒤 단계를 진행한다.
        Submission registered = versionRegistration.findRegisteredSubmissionWithSameHash(project, stored.sha256())
                .orElse(null);
        if (registered != null) {
            return judgeDuplicate(registered);
        }
        if (targetItemId != null) {
            return judgeManualTarget(project, saveSubmission(project, file, stored), targetItemId);
        }
        List<Submission> sameNamed = submissions
                .findByProjectIdAndOriginalFileNameOrderByIdDesc(project.getId(), file.getOriginalFilename());
        if (!sameNamed.isEmpty()) {
            return judgeSameNameReupload(project, file, stored, sameNamed);
        }
        return judgeByRules(project, saveSubmission(project, file, stored));
    }

    private Submission saveSubmission(Project project, MultipartFile file, StorageService.StoredFile stored) {
        return submissions.save(new Submission(project, file.getOriginalFilename(),
                stored.path().toString(), stored.sha256(), LocalDateTime.now()));
    }

    /**
     * 동일 파일명 재접수(REQ-outputhub-026): 같은 원본 파일명은 같은 대장 행의 파일로 본다.
     * 수락 전 동일 내용(해시) 재업로드는 대기 제안을 재사용하고(AC-006-1), 내용이 바뀌었으면 등록 이력이 있는
     * 행으로 새 버전 등록 제안을 만든다(REQ-outputhub-007·022). 등록된 버전과 해시가 같으면 이 앞 단계에서
     * 이미 중복으로 걸러진다(REQ-outputhub-006, 사용자 요구 2026-11-19).
     */
    private SuggestionView judgeSameNameReupload(Project project, MultipartFile file, StorageService.StoredFile stored,
                                                 List<Submission> sameNamed) {
        for (Submission previous : sameNamed) {
            if (previous.getSha256().equals(stored.sha256())) {
                MatchSuggestion pending = pendingSuggestionOf(previous);
                if (pending != null && pending.getVerdict() != MatchSuggestion.Verdict.DUPLICATE) {
                    return toView(pending);
                }
            }
            ChecklistItem target = registeredRow(previous);
            if (target != null) {
                return judgeReuploadToRow(project, saveSubmission(project, file, stored), target);
            }
        }
        return judgeByRules(project, saveSubmission(project, file, stored));
    }

    private MatchSuggestion pendingSuggestionOf(Submission submission) {
        return suggestions.findFirstBySubmissionIdAndStatusOrderByIdAsc(submission.getId(),
                MatchSuggestion.Status.PENDING).orElse(null);
    }

    /** 이전 접수가 버전으로 등록된 대장 행을 찾는다(REQ-outputhub-007 등록 이력). */
    private ChecklistItem registeredRow(Submission previous) {
        return versions.findFirstBySubmissionIdOrderByIdDesc(previous.getId())
                .map(version -> version.getDeliverable().getItem())
                .orElse(null);
    }

    /** 재접수 제안: 다음 버전 표기와 규칙 파일명 갱신 예정을 근거에 적는다(REQ-outputhub-022). */
    private SuggestionView judgeReuploadToRow(Project project, Submission submission, ChecklistItem target) {
        String nextVersionLabel = deliverables.findByItemId(target.getId())
                .map(deliverable -> VersionNaming.format(deliverable.peekNextVersionNo()))
                .orElse(VersionNaming.format(Deliverable.FIRST_VERSION_NO));
        String evidence = "동일 파일명 재접수 — " + target.getItemName() + " 행에 새 버전(v" + nextVersionLabel
                + ") 등록 제안. 수락하면 파일명을 규칙에 맞게 바꿔 최신본을 갱신합니다";
        MatchSuggestion suggestion = suggestions.save(new MatchSuggestion(project, submission,
                MatchSuggestion.Verdict.LINK, target, evidence));
        CandidateView candidate = new CandidateView(target.getId(), target.getItemName(), 0, evidence,
                target.getStatus().label());
        return new SuggestionView(suggestion.getId(), "행 연결", submission.getFileName(), evidence,
                List.of(candidate));
    }

    /** REQ-outputhub-021: 드롭다운으로 지정한 항목으로 연결 제안을 만든다(수락 전까지 행이 바뀌지 않는다). */
    private SuggestionView judgeManualTarget(Project project, Submission submission, Long targetItemId) {
        ChecklistItem target = items.findById(targetItemId).orElseThrow();
        MatchSuggestion suggestion = suggestions.save(new MatchSuggestion(project, submission,
                MatchSuggestion.Verdict.LINK, items.getReferenceById(targetItemId), MANUAL_TARGET_EVIDENCE));
        CandidateView candidate = new CandidateView(target.getId(), target.getItemName(), 0,
                MANUAL_TARGET_EVIDENCE, target.getStatus().label());
        return new SuggestionView(suggestion.getId(), "행 연결", submission.getFileName(),
                MANUAL_TARGET_EVIDENCE, List.of(candidate));
    }

    /** REQ-outputhub-004: 하이브리드 매칭(키워드+벡터 유사도)으로 대장 후보를 판정한다. */
    private SuggestionView judgeByRules(Project project, Submission submission) {
        List<MatchService.Candidate> candidates = matchService.rank(submission.getFileName(),
                Path.of(submission.getStoredPath()), toItemRefs(project));
        if (candidates.isEmpty()) {
            String evidence = "대장 후보 없음 → 자동등록 제안";
            MatchSuggestion suggestion = suggestions.save(new MatchSuggestion(project, submission,
                    MatchSuggestion.Verdict.NEW, null, evidence));
            return new SuggestionView(suggestion.getId(), "신규(자동등록)", submission.getFileName(),
                    evidence, List.of());
        }
        boolean ambiguous = candidates.size() > 1;
        StringBuilder evidence = new StringBuilder();
        for (MatchService.Candidate candidate : candidates) {
            if (!evidence.isEmpty()) {
                evidence.append(" / ");
            }
            evidence.append(candidate.itemName()).append(": ").append(candidate.evidence());
        }
        if (ambiguous) {
            evidence.append(" — 후보를 선택하세요(선택 전까지 행이 바뀌지 않습니다)");
        }
        MatchSuggestion suggestion = suggestions.save(new MatchSuggestion(project, submission,
                MatchSuggestion.Verdict.LINK, candidates.get(0).itemId() == null ? null
                : items.getReferenceById(candidates.get(0).itemId()), evidence.toString()));
        List<CandidateView> views = candidates.stream()
                .map(c -> new CandidateView(c.itemId(), c.itemName(), c.score(), c.evidence(),
                        items.findById(c.itemId()).map(row -> row.getStatus().label()).orElse("")))
                .toList();
        return new SuggestionView(suggestion.getId(), "행 연결", submission.getFileName(),
                evidence.toString(), views);
    }

    /**
     * REQ-outputhub-006(AC-006-3·4): 등록된 버전과 동일 해시인 접수는 파일명과 관계없이 중복으로 무효 처리한다.
     * 중복 안내는 응답으로만 보여 주는 1회성 알림이다(사용자 요구 2026-11-19): 제안 카드로 저장하지 않아
     * 접수함을 다시 열면 사라진다. 대기 제안 재사용(AC-006-1)은 동일 파일명·동일 해시 경로에서만 한다.
     */
    private SuggestionView judgeDuplicate(Submission existing) {
        return new SuggestionView(null, "중복", existing.getFileName(), DUPLICATE_EVIDENCE, List.of());
    }

    /** 접수함을 다시 열어도 대기 중 제안을 이어서 처리할 수 있게 목록을 돌려준다. 중복 안내는 1회성이라 목록에서 제외한다(과거 저장분 포함). */
    @Transactional(readOnly = true)
    @GetMapping("/api/projects/{id}/suggestions")
    public ResponseEntity<List<SuggestionView>> pendingSuggestions(@PathVariable Long id) {
        if (!projects.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<SuggestionView> views = suggestions.findByProjectIdAndStatusOrderByIdAsc(id, MatchSuggestion.Status.PENDING)
                .stream()
                .filter(suggestion -> suggestion.getVerdict() != MatchSuggestion.Verdict.DUPLICATE)
                .map(this::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    /** 대기 제안을 화면 카드 형태로 변환한다. 연결 제안은 저장된 대상 행을 후보로 내려준다. */
    private SuggestionView toView(MatchSuggestion suggestion) {
        String evidence = suggestion.getEvidence() == null ? "" : suggestion.getEvidence();
        List<CandidateView> candidates = new ArrayList<>();
        if (suggestion.getVerdict() == MatchSuggestion.Verdict.LINK && suggestion.getTargetItem() != null) {
            ChecklistItem target = suggestion.getTargetItem();
            candidates.add(new CandidateView(target.getId(), target.getItemName(), 0, evidence,
                    target.getStatus().label()));
        }
        return new SuggestionView(suggestion.getId(), suggestion.getVerdict().label(),
                suggestion.getSubmission().getFileName(), evidence, candidates);
    }

    /** REQ-outputhub-005: 신규(자동등록) 승인 — 필수=아니오 행을 추가하고 접수 처리한다. */
    @Transactional
    @PostMapping("/api/suggestions/{id}/approve-new")
    public ResponseEntity<?> approveNew(@PathVariable Long id) {
        MatchSuggestion suggestion = suggestions.findById(id).orElse(null);
        if (suggestion == null || suggestion.getStatus() != MatchSuggestion.Status.PENDING) {
            return ResponseEntity.notFound().build();
        }
        if (suggestion.getVerdict() != MatchSuggestion.Verdict.NEW) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("신규(자동등록) 제안만 승인할 수 있습니다");
        }
        if (versionRegistration.isSameHashAlreadyRegistered(suggestion.getProject(), suggestion.getSubmission())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("동일 해시 파일이 이미 등록되어 있습니다(REQ-outputhub-006 중복)");
        }
        ChecklistItem created = items.save(new ChecklistItem(suggestion.getProject(),
                AUTO_PHASE, suggestion.getSubmission().getFileName(), false));
        created.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(created);
        versionRegistration.register(created, suggestion.getSubmission(), "신규(자동등록) 승인");
        suggestion.accept();
        suggestions.save(suggestion);
        audits.save(new AuditLog(created, "신규 승인", suggestion.getEvidence(), "PMO", LocalDateTime.now()));
        return ResponseEntity.ok(created.getId());
    }

    /** REQ-outputhub-005: 신규(자동등록) 반려 — 제안을 제거한다. */
    @Transactional
    @DeleteMapping("/api/suggestions/{id}")
    public ResponseEntity<?> reject(@PathVariable Long id) {
        MatchSuggestion suggestion = suggestions.findById(id).orElse(null);
        if (suggestion == null) {
            return ResponseEntity.notFound().build();
        }
        if (suggestion.getVerdict() == MatchSuggestion.Verdict.NEW) {
            ChecklistItem ghost = suggestion.getTargetItem();
            if (ghost != null) {
                audits.save(new AuditLog(ghost, "신규 반려", suggestion.getSubmission().getFileName(), "PMO",
                        LocalDateTime.now()));
            }
        }
        suggestion.reject();
        suggestions.save(suggestion);
        return ResponseEntity.ok().build();
    }

    /**
     * 매칭 수락: 후보 행에 연결해 접수 처리(AC-004-1). 모호 파일은 행을 지정해 수락(AC-004-2).
     * REQ-outputhub-023: 검토대기 카드 드롭다운에서 행을 바꿔 수락할 수 있고, 신규(자동등록) 제안도
     * 행을 지정하면 연결 수락한다.
     */
    @Transactional
    @PostMapping("/api/suggestions/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestParam(required = false) Long itemId) {
        MatchSuggestion suggestion = suggestions.findById(id).orElse(null);
        if (suggestion == null || suggestion.getStatus() != MatchSuggestion.Status.PENDING) {
            return ResponseEntity.notFound().build();
        }
        if (suggestion.getVerdict() == MatchSuggestion.Verdict.NEW && itemId == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("신규(자동등록) 제안은 승인 API로 처리하세요");
        }
        if (suggestion.getVerdict() != MatchSuggestion.Verdict.LINK
                && suggestion.getVerdict() != MatchSuggestion.Verdict.NEW) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("행 연결 제안만 수락할 수 있습니다");
        }
        ChecklistItem target = itemId != null ? items.findById(itemId).orElse(null) : suggestion.getTargetItem();
        if (target == null) {
            return ResponseEntity.badRequest().body("연결할 대상 행을 선택하세요");
        }
        if (!target.getProject().getId().equals(suggestion.getProject().getId())) {
            return ResponseEntity.badRequest().body("다른 프로젝트의 산출물 항목입니다");
        }
        if (target.getStatus() == ChecklistItem.Status.CONFIRMED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("확정본이 있습니다. 교체 승인 후 반영됩니다(AC-008-1)");
        }
        if (versionRegistration.isSameHashAlreadyRegistered(target.getProject(), suggestion.getSubmission())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("동일 해시 파일이 이미 등록되어 있습니다(REQ-outputhub-006 중복)");
        }
        String evidence = acceptEvidence(suggestion, target);
        target.transitionTo(ChecklistItem.Status.SUBMITTED);
        items.save(target);
        versionRegistration.register(target, suggestion.getSubmission(), evidence);
        suggestion.accept();
        suggestions.save(suggestion);
        audits.save(new AuditLog(target, "접수", suggestion.getSubmission().getFileName() + " — "
                + evidence, "PMO", LocalDateTime.now()));
        return ResponseEntity.ok(target.getId());
    }

    /** 수락 근거: 제안된 행 외의 행을 직접 골랐거나 신규 제안을 행에 연결했으면 드롭다운 근거를 덧붙인다(REQ-outputhub-023). */
    private String acceptEvidence(MatchSuggestion suggestion, ChecklistItem target) {
        boolean suggestedTarget = suggestion.getVerdict() == MatchSuggestion.Verdict.LINK
                && suggestion.getTargetItem() != null
                && target.getId().equals(suggestion.getTargetItem().getId());
        if (suggestedTarget) {
            return suggestion.getEvidence();
        }
        return suggestion.getEvidence() == null ? MANUAL_PICK_EVIDENCE
                : suggestion.getEvidence() + " — " + MANUAL_PICK_EVIDENCE;
    }

    private List<MatchService.ItemRef> toItemRefs(Project project) {
        return items.findByProjectIdOrderByIdAsc(project.getId()).stream()
                .map(row -> new MatchService.ItemRef(row.getId(), row.getItemName()))
                .toList();
    }
}
