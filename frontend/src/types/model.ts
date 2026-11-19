export type ItemStatus = "미완료" | "검토 필요" | "보완 필요" | "검토완료";

export interface Project {
  id: number;
  name: string;
  vendor: string;
}

export interface PhaseProgress {
  name: string;
  total: number;
  submitted: number;
  /** 검토완료 상태 행 수(단계별 완료율 분자) */
  confirmed: number;
  /** 검토완료 기준 완료율 */
  pct: number;
  /** 제출 기준 완료율 */
  submittedPct: number;
  /** 마감일이 지났는데 검토완료가 아닌 행 수(REQ-outputhub-028) */
  overdueCount: number;
}

export interface ChecklistRow {
  id: number;
  phase: string;
  itemName: string;
  required: boolean;
  status: ItemStatus;
  supplementReason: string | null;
  latestVersionNo: number;
  /** 표기용 최신 버전(REQ-outputhub-022): "0.1" · "0.11" · "1.0" … 접수 전이면 null */
  latestVersionLabel: string | null;
  /** 최신본 파일 1개(REQ-outputhub-023): 주간보고 누적 항목도 최신본만 내려온다. 전체 버전은 상세 화면(REQ-outputhub-024)에서 본다 */
  latestFileName: string | null;
  latestDownloadUrl: string | null;
  evidence: string | null;
  /** 기준 마감일(REQ-outputhub-028, ISO yyyy-MM-dd). 미지정(주기 접수 항목 등)이면 null */
  dueDate: string | null;
  /** 마감일이 지났는데 검토완료가 아니면 true(REQ-outputhub-028) */
  overdue: boolean;
}

export interface DashboardView {
  project: Project;
  overallPct: number;
  /** 전체 대장 행 중 검토완료 건수(‘전체 N종 중 K종 완료’ 표시용) */
  confirmedCount: number;
  /** 통계 집계 대상 행 수(주간보고 누적 항목 제외) */
  totalCount: number;
  phases: PhaseProgress[];
  badges: string[];
  rows: ChecklistRow[];
}

export interface MatchCandidate {
  itemId: number;
  itemName: string;
  score: number;
  evidence: string;
  /** 대장 행의 현재 검토 상태(검토완료 재업로드 확인 팝업 판단용) */
  itemStatus: string;
}

/** 수락·반려·승인으로 처리할 수 있는 제안 카드. 접수함을 다시 열어도 대기 목록에 남는다. */
export interface SuggestionCardView {
  suggestionId: number;
  verdict: "행 연결" | "신규(자동등록)";
  fileName: string;
  evidence: string;
  candidates: MatchCandidate[];
}

/** 중복 안내(사용자 요구 2026-11-19): 업로드 응답으로만 보여 주고 제안으로 저장하지 않아 접수함을 다시 열면 사라진다. */
export interface DuplicateNoticeView {
  suggestionId: null;
  verdict: "중복";
  fileName: string;
  evidence: string;
}

export type SuggestionView = SuggestionCardView | DuplicateNoticeView;

export interface VersionView {
  versionId: number;
  versionNo: number;
  /** 표기용 버전(REQ-outputhub-022): 0.1 · 0.11 · 1.0 · 1.01 … */
  versionLabel: string;
  fileName: string;
  old: boolean;
  uploadedAt: string;
  downloadUrl: string;
}

/** 상태 변경 응답(보완 필요 지정·검토완료·교체 승인 공통). */
export interface StatusView {
  status: ItemStatus | string;
}

/** 접수함 업로드 드롭다운용 산출물 항목(REQ-outputhub-021). */
export interface ItemOption {
  id: number;
  phase: string;
  itemName: string;
  status: ItemStatus;
}

export interface AuditView {
  id: number;
  action: string;
  detail: string;
  actor: string;
  at: string;
}

export interface ItemDetail {
  itemId: number;
  phase: string;
  itemName: string;
  required: boolean;
  status: ItemStatus;
  supplementReason: string | null;
  /** 기준 마감일(ISO yyyy-MM-dd). 미지정이면 null(REQ-outputhub-028) */
  dueDate: string | null;
  versions: VersionView[];
  audits: AuditView[];
}
