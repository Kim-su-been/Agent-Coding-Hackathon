<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { fetchItemDetail, replaceConfirmed } from "../api/client";
import ReviewModal from "../components/ReviewModal.vue";
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
      <h2>{{ detail.itemName }} <span class="badge">{{ detail.status }}</span></h2>
      <p class="sub">단계: {{ detail.phase }} · 필수: {{ detail.required ? "예" : "아니오" }} · PMO 단일 역할(로그인 없음)</p>
      <p v-if="detail.supplementReason" class="reason">보완 사유: {{ detail.supplementReason }}</p>
    </header>

    <!-- 검토 전(검토 필요·보완 필요): 파일 버전 내역과 감사 이력을 별도 표로 보여준다(REQ-outputhub-024) -->
    <div v-if="!confirmed" class="columns">
      <div>
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
    <div v-else class="merged">
      <h3>이력(감사 + 파일 버전)</h3>
      <p class="hint">검토완료된 항목입니다. 파일 버전 내역이 감사 이력 표와 합쳐져 하나의 이력으로 표시됩니다.</p>
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
header h2 { font: 700 var(--title2); margin: 0; }
h3 { font: 700 var(--subtitle); margin: 0 0 var(--space-2); }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); }
.review { padding: 6px 12px; font-size: 14px; }
/* 입력은 테두리 없이 밑줄 스타일 기본, 입력 중(포커스)에 밑줄이 파란색으로 활성화 */
input { border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 8px 4px; font: 400 15px/22px var(--font-sans); color: var(--grey-900); background: transparent; outline: none; }
input::placeholder { color: var(--grey-500); }
input:focus { border-bottom-color: var(--blue-600); }
.badge { background: var(--blue-50); color: var(--blue-600); padding: 4px 12px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 600; vertical-align: middle; }
.sub { color: var(--grey-500); font-size: 13px; line-height: 20px; }
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
