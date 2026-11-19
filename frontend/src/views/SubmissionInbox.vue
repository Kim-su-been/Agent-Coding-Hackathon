<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import {
  acceptSuggestion,
  approveNewSuggestion,
  fetchPendingSuggestions,
  fetchProjectItems,
  rejectSuggestion,
  replaceConfirmed,
  uploadFiles,
} from "../api/client";
import { useProjects } from "../composables/useProjects";
import type { ItemOption, MatchCandidate, SuggestionCardView, SuggestionView } from "../types/model";

const { projects, selectedProjectId, loadProjects, loadDashboard } = useProjects();
const suggestions = ref<SuggestionView[]>([]);
const uploading = ref(false);
const message = ref("");
/** 카드별 연결 대상 행(REQ-outputhub-023). 기본값은 제안된 후보, 바꾸면 그 행으로 수락한다. */
const selectedCandidates = ref<Record<number, number | "">>({});
const itemOptions = ref<ItemOption[]>([]);
const targetItemId = ref<number | "">("");
/** 검토 대기 제안 목록. 중복 안내는 제안이 아니므로 제외한다(사용자 요구 2026-11-19). */
const progressList = computed(() =>
  suggestions.value
    .filter((s): s is SuggestionCardView => s.verdict !== "중복")
    .map((s) => ({
      fileName: s.fileName,
      verdict: s.verdict,
      suggestionId: s.suggestionId,
    })),
);

function verdictClass(verdict: string): string {
  if (verdict === "중복") return "s-dup";
  if (verdict === "신규(자동등록)") return "s-new";
  return "s-link";
}

async function loadPending(): Promise<void> {
  if (selectedProjectId.value === null) {
    suggestions.value = [];
    return;
  }
  try {
    suggestions.value = await fetchPendingSuggestions(selectedProjectId.value);
    initCardTargets(suggestions.value);
  } catch (e) {
    message.value = e instanceof Error ? e.message : "대기 제안 조회에 실패했습니다";
  }
}

/** 카드별 연결 대상 기본값: 제안된 후보 행(REQ-outputhub-023). 신규(자동등록) 카드는 미선택("") 이다. */
function initCardTargets(list: SuggestionView[]): void {
  for (const s of list) {
    if (s.verdict === "중복" || s.suggestionId in selectedCandidates.value) {
      continue;
    }
    selectedCandidates.value[s.suggestionId] = s.candidates[0]?.itemId ?? "";
  }
}

/** 업로드 판정 결과와 대기 제안을 합친다. 같은 제안은 한 번만 남기고(방금 판정 우선), 중복 안내는 1회성으로 그대로 보여 준다. */
function mergeCards(judged: SuggestionView[], pending: SuggestionView[]): SuggestionView[] {
  const seen = new Set<number>();
  const merged: SuggestionView[] = [];
  for (const card of [...judged, ...pending]) {
    if (card.verdict === "중복") {
      merged.push(card);
      continue;
    }
    if (seen.has(card.suggestionId)) {
      continue;
    }
    seen.add(card.suggestionId);
    merged.push(card);
  }
  return merged;
}

function hasRowPick(s: SuggestionCardView): boolean {
  return typeof selectedCandidates.value[s.suggestionId] === "number";
}

/** 카드 드롭다운에서 고른 연결 대상. 제안 후보 목록과 항목 옵션에서 상태를 함께 찾는다. */
function chosenTarget(s: SuggestionCardView): MatchCandidate | undefined {
  const chosen = selectedCandidates.value[s.suggestionId];
  if (typeof chosen !== "number") {
    return undefined;
  }
  const fromCandidates = s.candidates.find((c) => c.itemId === chosen);
  if (fromCandidates) {
    return fromCandidates;
  }
  const option = itemOptions.value.find((o) => o.id === chosen);
  if (!option) {
    return undefined;
  }
  return { itemId: option.id, itemName: option.itemName, score: 0, evidence: "", itemStatus: option.status };
}

/** 업로드 바의 산출물 항목 드롭다운 목록(REQ-outputhub-021). */
async function loadItemOptions(): Promise<void> {
  if (selectedProjectId.value === null) {
    itemOptions.value = [];
    targetItemId.value = "";
    return;
  }
  try {
    itemOptions.value = await fetchProjectItems(selectedProjectId.value);
  } catch {
    itemOptions.value = [];
  }
}

onMounted(async () => {
  await loadProjects();
  await loadItemOptions();
  await loadPending();
});

watch(selectedProjectId, () => {
  selectedCandidates.value = {};
  targetItemId.value = "";
  loadItemOptions();
  loadPending();
});

async function onPick(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  if (!input.files || input.files.length === 0 || selectedProjectId.value === null) {
    message.value = "프로젝트를 먼저 선택하세요";
    return;
  }
  uploading.value = true;
  message.value = "";
  try {
    const chosenItemId = targetItemId.value === "" ? undefined : targetItemId.value;
    const judged = await uploadFiles(selectedProjectId.value, Array.from(input.files), chosenItemId);
    const pending = await fetchPendingSuggestions(selectedProjectId.value);
    suggestions.value = mergeCards(judged, pending);
    initCardTargets(suggestions.value);
    message.value = `업로드 완료 — 검토 대기 제안 ${progressList.value.length}건을 이 화면에서 처리하세요`;
  } catch (e) {
    message.value = e instanceof Error ? e.message : "업로드에 실패했습니다";
  } finally {
    uploading.value = false;
    input.value = "";
  }
}

function removeSuggestion(suggestionId: number): void {
  suggestions.value = suggestions.value.filter((s) => s.suggestionId !== suggestionId);
  delete selectedCandidates.value[suggestionId];
}

async function accept(s: SuggestionCardView): Promise<void> {
  const target = chosenTarget(s);
  if (!target) {
    message.value = "연결할 산출물 항목을 먼저 선택하세요";
    return;
  }

  // REQ-outputhub-008: 검토완료 항목에 새 파일을 다시 올릴 때는 확인 팝업을 먼저 띄운다.
  if (target.itemStatus === "검토완료") {
    const confirmed = window.confirm(
      `"${target.itemName}" 항목은 검토완료가 된 항목입니다. 정말 다시 업로드하시겠습니까?\n`
      + "확인하면 상태가 검토 필요(검토전)로 돌아가고 새 파일이 새 버전으로 등록됩니다. 이전 검토완료 파일은 old로 보존됩니다.",
    );
    if (!confirmed) {
      message.value = "취소했습니다. 기존 검토완료 파일을 그대로 유지합니다";
      return;
    }
    try {
      await replaceConfirmed(target.itemId, s.suggestionId);
      removeSuggestion(s.suggestionId);
      message.value = "재업로드 완료 — 상태가 검토 필요(검토전)로 돌아가고 새 파일이 새 버전으로 등록되었습니다";
      await loadDashboard();
    } catch (e) {
      message.value = e instanceof Error ? e.message : "재업로드에 실패했습니다";
    }
    return;
  }

  try {
    await acceptSuggestion(s.suggestionId, target.itemId);
    removeSuggestion(s.suggestionId);
    message.value = "수락 완료 — 나머지 대기 제안도 이 화면에서 이어서 수락할 수 있습니다";
    await loadDashboard();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "수락에 실패했습니다";
  }
}

async function approveNew(s: SuggestionCardView): Promise<void> {
  try {
    await approveNewSuggestion(s.suggestionId);
    removeSuggestion(s.suggestionId);
    message.value = "신규(자동등록) 승인 완료 — 나머지 대기 제안도 이 화면에서 이어서 처리할 수 있습니다";
    await loadProjects();
    await loadDashboard();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "승인에 실패했습니다";
  }
}

async function reject(s: SuggestionCardView): Promise<void> {
  try {
    await rejectSuggestion(s.suggestionId);
    removeSuggestion(s.suggestionId);
    message.value = "반려 완료";
  } catch (e) {
    message.value = e instanceof Error ? e.message : "반려에 실패했습니다";
  }
}
</script>

<template>
  <section class="inbox">
    <div class="upload-bar">
      <select v-model.number="selectedProjectId">
        <option v-for="project in projects" :key="project.id" :value="project.id">
          {{ project.name }}
        </option>
      </select>
      <select v-model="targetItemId" aria-label="접수할 산출물 항목">
        <option value="">자동 매칭(파일명 규칙)</option>
        <option v-for="item in itemOptions" :key="item.id" :value="item.id">
          {{ item.phase }} · {{ item.itemName }}
        </option>
      </select>
      <span class="hint">항목을 고르면 자동 매칭 대신 그 행으로 접수됩니다. 수락하면 파일명이 {항목}_v{버전}_{날짜} 로 바뀌고, 검토완료 시 {항목}_v1.0 으로 저장됩니다. 같은 파일명을 다시 올리면 새 파일로 보고 새 버전으로 접수됩니다</span>
    </div>
    <div class="dropzone">
      <label class="pick">
        <input type="file" multiple @change="onPick" />
        파일을 선택하거나 여기로 끌어다 놓으세요(다중 선택)
      </label>
      <p v-if="uploading">업로드 중…</p>
      <p class="msg">{{ message }}</p>
    </div>

    <article v-for="(s, index) in suggestions" :key="s.suggestionId ?? `dup-${index}`" class="card">
      <header>
        <span class="verdict" :class="verdictClass(s.verdict)">{{ s.verdict }}</span>
        <strong>{{ s.fileName }}</strong>
      </header>

      <p class="evidence">매칭 근거: {{ s.evidence }}</p>

      <div v-if="s.verdict !== '중복'" class="candidates">
        <label class="target-pick">
          연결할 산출물 항목
          <select
            v-model="selectedCandidates[s.suggestionId]"
            :aria-label="'연결할 산출물 항목: ' + s.fileName"
          >
            <option v-if="s.verdict === '신규(자동등록)'" value="">— 신규(자동등록)으로 승인 —</option>
            <option v-for="opt in itemOptions" :key="opt.id" :value="opt.id">
              {{ opt.phase }} · {{ opt.itemName }} ({{ opt.status }})
            </option>
          </select>
        </label>
        <p v-if="s.candidates.length > 1" class="hint">
          제안 후보 {{ s.candidates.length }}개는 위 매칭 근거에 점수와 함께 표시됩니다. 드롭다운에서 다른 행으로 바꿔 수락할 수 있습니다
        </p>
        <p v-else-if="s.verdict === '행 연결'" class="hint">드롭다운에서 다른 산출물 항목으로 바꿔 수락할 수도 있습니다</p>
      </div>

      <div class="actions">
        <template v-if="s.verdict === '행 연결'">
          <button class="primary" :disabled="!hasRowPick(s)" @click="accept(s)">
            수락
          </button>
          <button @click="reject(s)">거절</button>
        </template>
        <template v-else-if="s.verdict === '신규(자동등록)'">
          <button v-if="hasRowPick(s)" class="primary" @click="accept(s)">선택한 행으로 수락</button>
          <button v-else class="primary" @click="approveNew(s)">승인(필수=아니오로 등록)</button>
          <button @click="reject(s)">반려</button>
        </template>
        <span v-else class="dup-note">집계에 포함되지 않습니다</span>
      </div>
    </article>
  </section>
</template>

<style scoped>
.upload-bar { display: flex; align-items: center; gap: var(--space-3); margin-bottom: var(--space-3); }
.upload-bar select { padding: 10px 16px; border: 1px solid var(--grey-200); border-radius: var(--radius-sm); background: var(--surface); font: 600 15px/22px var(--font-sans); color: var(--grey-900); }
.upload-bar .hint { font-size: 13px; line-height: 20px; color: var(--grey-500); }
.dropzone { display: flex; align-items: center; gap: var(--space-4); background: var(--surface); border: 2px dashed var(--grey-400); border-radius: var(--radius-md); padding: var(--space-6); text-align: center; box-shadow: var(--shadow-card); }
.dropzone .mascot { height: 72px; flex: none; }
.dropzone .pick { text-align: left; font-size: 15px; line-height: 22px; }
.pick input { margin-right: var(--space-2); }
.msg { color: var(--blue-600); }
.card { background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-md); padding: var(--space-5); margin-top: var(--space-4); box-shadow: var(--shadow-card); }
.card header strong { font-size: 17px; line-height: 26px; }
.verdict { display: inline-block; padding: 4px 12px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 600; margin-right: var(--space-2); }
.s-link { background: var(--green-50); color: var(--green-600); }
.s-new { background: var(--blue-50); color: var(--blue-600); }
.s-dup { background: var(--red-50); color: var(--red-600); }
.evidence { color: var(--grey-500); font-size: 13px; line-height: 20px; }
.candidates label { display: block; margin: var(--space-1) 0; font-size: 15px; line-height: 22px; }
.target-pick { display: flex; align-items: center; gap: var(--space-2); font-size: 15px; line-height: 22px; }
.target-pick select { padding: 8px 12px; border: 1px solid var(--grey-200); border-radius: var(--radius-sm); background: var(--surface); font: 600 14px/20px var(--font-sans); color: var(--grey-900); min-width: 260px; }
.candidates .hint { color: var(--grey-500); font-size: 13px; line-height: 20px; margin: var(--space-1) 0 0; }
.actions { display: flex; gap: var(--space-2); margin-top: var(--space-3); align-items: center; }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); }
button:disabled { opacity: 0.5; cursor: not-allowed; }
.primary { background: var(--blue-600); border-color: var(--blue-600); color: #ffffff; }
.primary:hover { background: var(--blue-700); border-color: var(--blue-700); }
.dup-note { color: var(--red-600); font-size: 13px; line-height: 20px; }
</style>
