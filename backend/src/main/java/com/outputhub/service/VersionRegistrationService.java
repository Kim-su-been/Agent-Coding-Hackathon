package com.outputhub.service;

import com.outputhub.domain.AuditLog;
import com.outputhub.domain.ChecklistItem;
import com.outputhub.domain.Deliverable;
import com.outputhub.domain.Project;
import com.outputhub.domain.Submission;
import com.outputhub.domain.Version;
import com.outputhub.domain.VersionNaming;
import com.outputhub.storage.StorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 산출물(대장 행)에 접수 파일을 버전으로 등록한다(REQ-outputhub-007).
 * 등록과 검토완료 시점마다 REQ-outputhub-022 명명 규칙으로 원본 파일명을 바꿔 저장한다.
 */
@Service
public class VersionRegistrationService {

    /** 정합화 감사 로그 구성: 낡은 확정 기록의 파일명을 바로잡은 사실을 기록한다(REQ-outputhub-022). */
    private static final String RECONCILE_AUDIT_ACTION = "파일명 정합화";
    private static final String RECONCILE_AUDIT_ACTOR = "시스템";
    private static final String RECONCILE_AUDIT_REASON = "REQ-outputhub-022 검토완료 파일명 규칙 재적용";

    private final Deliverable.Repository deliverables;
    private final Version.Repository versions;
    private final Submission.Repository submissions;
    private final AuditLog.Repository audits;
    private final StorageService storage;

    public VersionRegistrationService(Deliverable.Repository deliverables, Version.Repository versions,
                                      Submission.Repository submissions, AuditLog.Repository audits,
                                      StorageService storage) {
        this.deliverables = deliverables;
        this.versions = versions;
        this.submissions = submissions;
        this.audits = audits;
        this.storage = storage;
    }

    /**
     * REQ-outputhub-006: 이 프로젝트 대장 행에 등록된 버전 파일 중 같은 해시(내용)를 가진 접수를 찾는다.
     * 중복 판정은 파일명과 관계없이 내용으로만 하고, 비교 대상은 실제 등록된 버전이다(사용자 요구 2026-11-19).
     * 수락 전 대기 접수·반려된 접수는 등록된 것이 아니므로 재업로드를 막지 않는다.
     */
    @Transactional(readOnly = true)
    public Optional<Submission> findRegisteredSubmissionWithSameHash(Project project, String sha256) {
        return versions.findByDeliverable_Item_ProjectIdOrderByIdAsc(project.getId()).stream()
                .map(Version::getSubmission)
                .filter(submission -> submission.getSha256().equals(sha256))
                .findFirst();
    }

    /**
     * REQ-outputhub-006: 등록 시점 재검사 — 제안을 만든 뒤에 같은 내용이 다른 접수로 먼저 등록됐을 수 있으므로
     * 수락·승인 시점에도 등록된 해시와 같으면 등록을 막는다. 제출 파일 자신이 등록한 버전은 제외한다.
     */
    @Transactional(readOnly = true)
    public boolean isSameHashAlreadyRegistered(Project project, Submission submission) {
        return versions.findByDeliverable_Item_ProjectIdOrderByIdAsc(project.getId()).stream()
                .anyMatch(version -> !version.getSubmission().getId().equals(submission.getId())
                        && version.getSubmission().getSha256().equals(submission.getSha256()));
    }

    /** 새 버전을 등록하고 이전 최신본을 old로 수집한다(주간보고 등 누적 항목 제외). */
    @Transactional
    public Version register(ChecklistItem item, Submission submission, String evidence) {
        Deliverable deliverable = deliverables.findByItemId(item.getId())
                .orElseGet(() -> deliverables.save(new Deliverable(item)));
        retirePreviousVersion(item, deliverable);
        int versionNo = deliverable.nextVersionNo();
        renameSubmissionFile(submission, VersionNaming.versionedFileName(item.getItemName(), versionNo,
                submission.getFileName(), LocalDate.now()));
        return versions.save(new Version(deliverable, versionNo, submission));
    }

    /**
     * REQ-outputhub-022: 검토완료 시 최신본 파일명을 {산출물 항목}_v1.0 으로 바꾸고,
     * 이후 재업로드 버전 번호가 v1.0 다음(v1.01…)이 되도록 카운터를 맞춘다. 바뀐 파일명을 돌려준다.
     */
    @Transactional
    public String renameLatestToConfirmed(ChecklistItem item) {
        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElse(null);
        if (deliverable == null) {
            return null;
        }
        Version latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElse(null);
        if (latest == null) {
            return null;
        }
        Submission submission = latest.getSubmission();
        String confirmedName = VersionNaming.confirmedFileName(item.getItemName(), submission.getFileName());
        renameSubmissionFile(submission, confirmedName);
        deliverable.applyConfirmedVersion();
        deliverables.save(deliverable);
        return confirmedName;
    }

    /**
     * REQ-outputhub-022: 검토완료 항목의 최신본이 규칙({항목}_v1.0.{확장자} 파일명, v1.0 다음 재수락 버전)에서
     * 벗어나 있으면 바로잡는다. 낡은 빌드에서 확정돼 파일명 변경이 없던 기록을 정리하는 데 쓴다.
     * 파일명을 바꿨으면 그 이름을, 바꿀 것이 없으면 null을 돌려준다.
     */
    @Transactional
    public String reconcileConfirmedFileName(ChecklistItem item) {
        Deliverable deliverable = deliverables.findByItemId(item.getId()).orElse(null);
        if (deliverable == null) {
            return null;
        }
        Version latest = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElse(null);
        if (latest == null) {
            return null;
        }
        return fixConfirmedRecord(item, deliverable, latest);
    }

    private String fixConfirmedRecord(ChecklistItem item, Deliverable deliverable, Version latest) {
        Submission submission = latest.getSubmission();
        String currentName = submission.getFileName();
        String confirmedName = VersionNaming.confirmedFileName(item.getItemName(), currentName);
        if (!currentName.equals(confirmedName)) {
            renameSubmissionFile(submission, confirmedName);
            deliverable.applyConfirmedVersion();
            deliverables.save(deliverable);
            audits.save(new AuditLog(item, RECONCILE_AUDIT_ACTION,
                    currentName + " → " + confirmedName + " — " + RECONCILE_AUDIT_REASON,
                    RECONCILE_AUDIT_ACTOR, LocalDateTime.now()));
            return confirmedName;
        }
        alignConfirmedCounter(deliverable);
        return null;
    }

    /** REQ-outputhub-022: 재수락 버전이 v1.01부터 이어지도록 카운터가 v1.0 미만이면 맞춘다(이미 맞으면 무시). */
    private void alignConfirmedCounter(Deliverable deliverable) {
        if (deliverable.getLatestVersionNo() < Deliverable.CONFIRMED_VERSION_NO) {
            deliverable.applyConfirmedVersion();
            deliverables.save(deliverable);
        }
    }

    /** 최신 버전의 제출 파일명을 돌려준다(버전이 없으면 null). 정합화 필요 여부 판단용이다. */
    @Transactional(readOnly = true)
    public String latestSubmissionFileName(ChecklistItem item) {
        return deliverables.findByItemId(item.getId())
                .flatMap(deliverable -> versions
                        .findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId()))
                .map(version -> version.getSubmission().getFileName())
                .orElse(null);
    }

    private void retirePreviousVersion(ChecklistItem item, Deliverable deliverable) {
        Version previous = versions.findFirstByDeliverableIdAndOldFalseOrderByVersionNoDesc(deliverable.getId())
                .orElse(null);
        if (previous == null || item.accumulatesFiles()) {
            return;
        }
        previous.markOld();
        versions.save(previous);
        Path moved = storage.collectOld(item.getProject().getName(),
                storage.resolve(previous.getSubmission().getStoredPath()));
        previous.getSubmission().relocate(moved.toString());
        submissions.save(previous.getSubmission());
    }

    private void renameSubmissionFile(Submission submission, String newFileName) {
        Path current = storage.resolve(submission.getStoredPath());
        if (!Files.exists(current)) {
            current = storage.recoverFromOld(submission.getProject().getName(), current);
        }
        if (current == null) {
            throw new IllegalStateException("저장소에서 원본 파일을 찾을 수 없습니다: " + submission.getFileName());
        }
        Path renamed = storage.rename(current, newFileName);
        submission.renameTo(newFileName, renamed.toString());
        submissions.save(submission);
    }
}
