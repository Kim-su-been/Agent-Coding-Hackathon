<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { fetchItemDetail, replaceConfirmed, updateDueDate } from "../api/client";
import ReviewModal from "../components/ReviewModal.vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useDueDate } from "../composables/useDueDate";
import { buildAuditHistory, buildMergedHistory, type HistoryEntry } from "../composables/useItemHistory";
import type { ItemDetail, VersionView } from "../types/model";

const props = defineProps<{ itemId: number }>();
const emit = defineEmits<{ (e: "back"): void }>();

const detail = ref<ItemDetail | null>(null);
const replaceSuggestionId = ref("");
const message = ref("");
const reviewVersion = ref<VersionView | null>(null);

/** 검토완료 상태: 파일 버전 내역이 감사 이력 표와 병합되어 하나의 이력 표로 보인다(REQ-outputhub-024). */
const confirmed = computed(() => detail.value?.status === "검토완료");
const mergedHistory = computed<HistoryEntry[]>(() =>
  detail.value ? buildMergedHistory(detail.value.versions, detail.value.audits) : [],
);

const { dueBadge } = useDueDate();

/** 마감일이 지났는데 검토완료가 아니면 지연 배지(REQ-outputhub-028). */
const overdue = computed(() => {
  if (!detail.value?.dueDate) {
    return false;
  }
  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  return detail.value.status !== "검토완료" && new Date(detail.value.dueDate).getTime() < startOfToday;
});
const dueBadgeInfo = computed(() => dueBadge(detail.value?.dueDate ?? null, overdue.value));

/** 마감일 인라인 수정: date 입력 변경 시 바로 저장하고 이력을 다시 내려받는다. */
async function onDueDateChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  try {
    await updateDueDate(props.itemId, input.value || null);
    message.value = "마감일이 저장되었습니다";
  } catch (e) {
    message.value = e instanceof Error ? e.message : "마감일 저장에 실패했습니다";
  }
  await reload();
}

/** 검토 전 감사 이력 표. 규칙 매칭 근거(키워드·벡터 유사도)는 감사 이력에서 숨긴다(사용자 요구 2026-11-19). */
const auditHistory = computed<HistoryEntry[]>(() =>
  detail.value ? buildAuditHistory(detail.value.audits) : [],
);

function back(): void {
  emit("back");
}

async function reload(): Promise<void> {
  detail.value = await fetchItemDetail(props.itemId);
}

function openReview(version: VersionView): void {
  reviewVersion.value = version;
}

async function onReviewed(): Promise<void> {
  reviewVersion.value = null;
  await reload();
}

function onCloseReview(): void {
  reviewVersion.value = null;
}

async function doReplace(): Promise<void> {
  const suggestionId = Number(replaceSuggestionId.value);
  if (!suggestionId) {
    message.value = "교체할 제안 번호를 입력하세요";
    return;
  }
  try {
    await replaceConfirmed(props.itemId, suggestionId);
    message.value = "교체 승인 완료(확정본은 old로 보존)";
    await reload();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "교체 승인에 실패했습니다";
  }
}

onMounted(reload);
</script>

<template>
  <section v-if="detail" class="detail">
    <button @click="back">← 대시보드</button>
    <header>
      <!-- 산출물 항목과 검토상태를 한 카드 박스에 묶어 눈에 띄게 보여 준다(사용자 요구 2026-11-19) -->
      <div class="summary-card">
        <div class="summary-row">
          <h2>{{ detail.itemName }}</h2>
          <StatusBadge :status="detail.status" class="summary-status" />
        </div>
        <div class="meta">
          <span class="meta-item">단계 {{ detail.phase }}</span>
          <span class="meta-item">
            마감일
            <input class="due-input" type="date" :value="detail.dueDate" @change="onDueDateChange" />
            <span v-if="dueBadgeInfo" :class="['due-badge', dueBadgeInfo.kind]">{{ dueBadgeInfo.label }}</span>
          </span>
        </div>
        <p v-if="detail.supplementReason" class="reason">보완 사유: {{ detail.supplementReason }}</p>
      </div>
    </header>

    <!-- 검토 전(검토 필요·보완 필요): 파일 버전 내역과 감사 이력을 별도 표로 보여준다(REQ-outputhub-024) -->
    <div v-if="!confirmed" class="columns">
      <!-- 파일 버전 내역을 카드 박스로 감싸 산출물 항목 박스와 대응되게 눈에 띄게 한다(사용자 요구 2026-11-19) -->
      <div class="panel">
        <h3>파일 버전 내역</h3>
        <table>
          <thead>
            <tr><th scope="col">버전</th><th scope="col">파일명</th><th scope="col">제출일시</th><th scope="col">old</th><th scope="col">동작</th></tr>
          </thead>
          <tbody>
            <tr v-for="v in detail.versions" :key="v.versionId">
              <td>v{{ v.versionLabel }}</td>
              <td><a :href="v.downloadUrl">{{ v.fileName }}</a></td>
              <td>{{ v.uploadedAt }}</td>
              <td><span v-if="v.old" class="old">old</span><span v-else class="muted">최신</span></td>
              <td><button class="review" @click="openReview(v)">검토하기</button></td>
            </tr>
            <tr v-if="detail.versions.length === 0">
              <td colspan="5" class="muted">아직 제출본이 없습니다</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div>
        <h3>감사 이력</h3>
        <table>
          <thead><tr><th scope="col">동작</th><th scope="col">내용</th><th scope="col">주체</th><th scope="col">일시</th></tr></thead>
          <tbody>
            <tr v-for="entry in auditHistory" :key="entry.key">
              <td>{{ entry.action }}</td><td>{{ entry.content }}</td><td>{{ entry.actor }}</td><td>{{ entry.at }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 검토완료: 파일 버전 행이 감사 이력 표와 시간순으로 병합된다(REQ-outputhub-024, AC-024-3) -->
    <div v-else class="panel merged">
      <h3 class="section-title">파일 내역</h3>
      <p class="hint">검토완료된 항목입니다. 파일 내역이 감사 이력 표와 합쳐져 하나의 이력으로 표시됩니다.</p>
      <table>
        <thead><tr><th scope="col">구분</th><th scope="col">동작</th><th scope="col">내용</th><th scope="col">주체</th><th scope="col">일시</th></tr></thead>
        <tbody>
          <tr v-for="entry in mergedHistory" :key="entry.key">
            <td><span :class="entry.kind === '파일' ? 'file-tag' : 'audit-tag'">{{ entry.kind }}</span></td>
            <td>{{ entry.action }}</td>
            <td>
              <a v-if="entry.downloadUrl" :href="entry.downloadUrl">{{ entry.content }}</a>
              <template v-else>{{ entry.content }}</template>
            </td>
            <td>{{ entry.actor }}</td>
            <td>{{ entry.at }}</td>
          </tr>
          <tr v-if="mergedHistory.length === 0">
            <td colspan="5" class="muted">이력이 없습니다</td>
          </tr>
        </tbody>
      </table>
    </div>

    <p class="msg">{{ message }}</p>
    <div class="actions">
      <input v-model="replaceSuggestionId" placeholder="교체할 제안 번호(확정 상태)" />
      <button @click="doReplace">교체 승인</button>
    </div>

    <ReviewModal
      v-if="reviewVersion"
      :item-id="itemId"
      :version="reviewVersion"
      :visible="true"
      @close="onCloseReview"
      @reviewed="onReviewed"
    />
  </section>
</template>

<style scoped>
header h2 { font: 700 var(--title-item); margin: 0; }
/* 산출물 항목·검토상태 요약 카드: 흰 카드에 테두리·그림자로 회색 화면 배경 위에서 눈에 띄게 한다(사용자 요구 2026-11-19) */
.summary-card { background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-lg); box-shadow: var(--shadow-card); padding: var(--space-5) var(--space-6); margin-top: var(--space-4); }
.summary-row { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); }
.summary-status { font: 700 15px/24px var(--font-sans); padding: 6px 14px; }
h3 { font: 700 var(--subtitle); margin: 0 0 var(--space-2); }
/* 산출물 항목과 같은 글씨(사용자 요구 2026-11-19): 검토완료 화면의 파일 내역 제목 */
.section-title { font: 700 var(--title-item); margin: 0 0 var(--space-2); }
/* 산출물 항목 아래 단계·마감일은 기본 본문보다 크게 보여 주고, 마감일은 인라인 수정 가능(사용자 요구 2026-11-19) */
.meta { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-6); margin: var(--space-2) 0 var(--space-1); font: 600 var(--meta-strong) var(--font-sans); color: var(--grey-900); }
.meta-item { display: inline-flex; align-items: center; gap: var(--space-2); }
.due-input { width: 150px; font: 600 var(--meta-strong) var(--font-sans); color: var(--grey-900); border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 2px 4px; background: transparent; outline: none; }
.due-input:focus { border-bottom-color: var(--blue-600); }
.due-badge { font-size: 13px; font-weight: 700; padding: 2px 8px; border-radius: var(--radius-sm); }
.due-badge.overdue { background: var(--red-50); color: var(--red-600); }
.due-badge.today { background: var(--blue-50); color: var(--blue-600); }
.due-badge.normal { background: var(--grey-100); color: var(--grey-700); }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); }
.review { padding: 6px 12px; font-size: 14px; }
/* 입력은 테두리 없이 밑줄 스타일 기본, 입력 중(포커스)에 밑줄이 파란색으로 활성화 */
input { border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 8px 4px; font: 400 15px/22px var(--font-sans); color: var(--grey-900); background: transparent; outline: none; }
input::placeholder { color: var(--grey-500); }
input:focus { border-bottom-color: var(--blue-600); }
/* 파일 내역 카드: 표를 박스로 감싸 산출물 항목 박스와 같은 강조로 보여 준다(사용자 요구 2026-11-19) */
.panel { background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-lg); box-shadow: var(--shadow-card); padding: var(--space-4) var(--space-5); }
.reason { color: var(--red-600); }
.columns { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-6); margin-top: var(--space-3); }
.merged { margin-top: var(--space-3); }
.hint { color: var(--grey-500); font-size: 13px; line-height: 20px; margin: 0 0 var(--space-2); }
table { width: 100%; border-collapse: collapse; background: var(--surface); font-size: 15px; line-height: 22px; }
th, td { text-align: left; padding: 12px 16px; border-bottom: 1px solid var(--grey-200); }
th { background: var(--grey-50); color: var(--grey-700); font-weight: 600; }
td a { color: var(--blue-600); text-decoration: none; }
.old { background: var(--grey-100); color: var(--grey-700); font-size: 11px; padding: 1px 6px; border-radius: var(--radius-sm); }
.file-tag, .audit-tag { font-size: 12px; font-weight: 600; padding: 2px 8px; border-radius: var(--radius-sm); }
.file-tag { background: var(--blue-50); color: var(--blue-600); }
.audit-tag { background: var(--grey-100); color: var(--grey-700); }
.msg { color: var(--blue-600); }
.actions { display: flex; gap: var(--space-2); margin: var(--space-3) 0; align-items: center; flex-wrap: wrap; }
.muted { color: var(--grey-400); }
</style>
