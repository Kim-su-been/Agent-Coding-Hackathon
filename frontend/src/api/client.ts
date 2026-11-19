import type { ChecklistRow, DashboardView, ItemDetail, ItemOption, Project, StatusView, SuggestionView } from "../types/model";

const BASE = "/api";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, init);
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `요청 실패: ${response.status}`);
  }
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export async function listProjects(): Promise<Project[]> {
  return request<Project[]>("/projects");
}

/** 프로젝트 추가: 기본 세팅대로 템플릿 전체 단계 산출물 목록이 자동 등록된다(REQ-outputhub-014). */
export async function createProject(body: { name: string; vendor: string }): Promise<Project> {
  return request<Project>("/projects", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function fetchDashboard(projectId: number): Promise<DashboardView> {
  return request<DashboardView>(`/projects/${projectId}/dashboard`);
}

/** 접수함 업로드 바의 산출물 항목 드롭다운 목록(REQ-outputhub-021). */
export async function fetchProjectItems(projectId: number): Promise<ItemOption[]> {
  return request<ItemOption[]>(`/projects/${projectId}/items`);
}

export async function addProjectItem(
  projectId: number,
  body: { phase: string; itemName: string; required: boolean; dueDate: string | null },
): Promise<ChecklistRow> {
  return request<ChecklistRow>(`/projects/${projectId}/items`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function deleteProjectItem(projectId: number, itemId: number): Promise<void> {
  await request<void>(`/projects/${projectId}/items/${itemId}`, { method: "DELETE" });
}

export async function uploadFiles(
  projectId: number,
  files: File[],
  targetItemId?: number,
): Promise<SuggestionView[]> {
  const form = new FormData();
  for (const file of files) {
    form.append("files", file);
  }
  if (targetItemId !== undefined) {
    form.append("targetItemId", String(targetItemId));
  }
  return request<SuggestionView[]>(`/projects/${projectId}/submissions`, { method: "POST", body: form });
}

export async function fetchPendingSuggestions(projectId: number): Promise<SuggestionView[]> {
  return request<SuggestionView[]>(`/projects/${projectId}/suggestions`);
}

export async function acceptSuggestion(suggestionId: number, itemId?: number): Promise<number> {
  const query = itemId ? `?itemId=${itemId}` : "";
  return request<number>(`/suggestions/${suggestionId}/accept${query}`, { method: "POST" });
}

export async function rejectSuggestion(suggestionId: number): Promise<void> {
  await request<void>(`/suggestions/${suggestionId}`, { method: "DELETE" });
}

export async function approveNewSuggestion(suggestionId: number): Promise<number> {
  return request<number>(`/suggestions/${suggestionId}/approve-new`, { method: "POST" });
}

export async function fetchItemDetail(itemId: number): Promise<ItemDetail> {
  return request<ItemDetail>(`/deliverables/${itemId}`);
}

/** 상세 화면에서 마감일 수정(사용자 요구 2026-11-19). dueDate null 이면 미지정으로 해제. */
export async function updateDueDate(itemId: number, dueDate: string | null): Promise<string> {
  const response = await request<StatusView>(`/deliverables/${itemId}/due-date`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ dueDate }),
  });
  return response.status;
}

export async function confirmItem(itemId: number): Promise<string> {
  const response = await request<StatusView>(`/deliverables/${itemId}/confirm`, { method: "POST" });
  return response.status;
}

export async function requestSupplement(itemId: number, reason: string): Promise<string> {
  const response = await request<StatusView>(`/deliverables/${itemId}/request-supplement`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
  });
  return response.status;
}

export async function replaceConfirmed(itemId: number, suggestionId: number): Promise<string> {
  const response = await request<StatusView>(`/deliverables/${itemId}/replace-confirmed`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason: String(suggestionId) }),
  });
  return response.status;
}

export function exportDashboardUrl(projectId: number): string {
  return `${BASE}/projects/${projectId}/export/dashboard.xlsx`;
}

export function exportDeliverablesUrl(projectId: number): string {
  return `${BASE}/projects/${projectId}/export/deliverables.zip`;
}
