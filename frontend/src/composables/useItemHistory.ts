import type { AuditView, VersionView } from "../types/model";

/** 상세 화면 이력 한 행(파일 버전 또는 감사 기록, REQ-outputhub-024). */
export interface HistoryEntry {
  key: string;
  kind: "파일" | "감사";
  action: string;
  /** 표시 내용. 파일 행은 "파일 v0.11 — 파일명 (old)" 꼴이고 downloadUrl 이 있으면 링크로 연다 */
  content: string;
  downloadUrl: string | null;
  actor: string;
  at: string;
}

/** 접수 파일을 올린 주체(PMO 단일 역할, D-001). 감사 기록과 같은 표기로 맞춘다. */
const UPLOADER = "PMO";

/** 규칙 매칭이 만든 근거 문구(파일명·문서 내용 키워드, 벡터 유사도). 감사 이력에는 표시하지 않고
 * 매칭 제안 카드(REQ-outputhub-013)에만 표시한다(사용자 요구 2026-11-19). */
const MATCH_EVIDENCE_MARKERS = ["파일명 키워드", "문서 내용 키워드", "벡터 유사도"];

/** 행을 바꿔 수락한 사실(REQ-outputhub-023)은 매칭 근거가 아니라 수락 경로라 감사 이력에 남긴다. */
const MANUAL_PICK_MARKER = "검토대기에서 산출물 항목을 직접 선택했습니다(드롭다운)";

/** 감사 내용에서 규칙 매칭 근거 부분을 숨긴다. 접수 기록은 "파일명 — 근거…" 꼴이라 파일명만 남기고,
 * 행을 직접 선택한 기록은 그 근거를 덧붙여 돌려준다. */
function withoutMatchEvidence(detail: string): string {
  const hasMatchEvidence = MATCH_EVIDENCE_MARKERS.some((marker) => detail.includes(marker));
  if (!hasMatchEvidence) {
    return detail;
  }
  const separator = detail.indexOf(" — ");
  if (separator === -1) {
    return "";
  }
  const fileName = detail.slice(0, separator).trim();
  if (detail.includes(MANUAL_PICK_MARKER)) {
    return `${fileName} — ${MANUAL_PICK_MARKER}`;
  }
  return fileName;
}

/** 파일 버전 한 행을 이력 행으로 바꾼다. */
function fileEntry(version: VersionView): HistoryEntry {
  const marker = version.old ? " (old)" : " (최신본)";
  return {
    key: `file-${version.versionId}`,
    kind: "파일",
    action: "업로드",
    content: `파일 v${version.versionLabel} — ${version.fileName}${marker}`,
    downloadUrl: version.downloadUrl,
    actor: UPLOADER,
    at: version.uploadedAt,
  };
}

/** 감사 기록 한 행을 이력 행으로 바꾼다. */
function auditEntry(audit: AuditView): HistoryEntry {
  return {
    key: `audit-${audit.id}`,
    kind: "감사",
    action: audit.action,
    content: withoutMatchEvidence(audit.detail),
    downloadUrl: null,
    actor: audit.actor,
    at: audit.at,
  };
}

/** 감사 기록 목록을 감사 이력 표 행으로 바꾼다(검토 전 감사 이력 표). */
export function buildAuditHistory(audits: AuditView[]): HistoryEntry[] {
  return audits.map(auditEntry);
}

/** 검토완료 상태에서 파일 버전 행과 감사 기록을 시간순으로 합친다(REQ-outputhub-024, AC-024-3). */
export function buildMergedHistory(versions: VersionView[], audits: AuditView[]): HistoryEntry[] {
  return [...versions.map(fileEntry), ...audits.map(auditEntry)].sort((a, b) =>
    a.at < b.at ? -1 : a.at > b.at ? 1 : 0,
  );
}
