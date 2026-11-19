<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { confirmItem, requestSupplement } from "../api/client";
import type { VersionView } from "../types/model";

const props = defineProps<{ itemId: number; version: VersionView; visible: boolean }>();
const emit = defineEmits<{ (e: "close"): void; (e: "reviewed"): void }>();

const reason = ref("");
const message = ref("");
const busy = ref(false);
const reasonInput = ref<HTMLInputElement | null>(null);

watch(
  () => props.visible,
  async (open) => {
    if (!open) return;
    reason.value = "";
    message.value = "";
    await nextTick();
    reasonInput.value?.focus();
  },
);

async function doConfirm(): Promise<void> {
  busy.value = true;
  try {
    await confirmItem(props.itemId);
    emit("reviewed");
  } catch (e) {
    message.value = e instanceof Error ? e.message : "검토완료 처리에 실패했습니다";
  } finally {
    busy.value = false;
  }
}

async function doSupplement(): Promise<void> {
  if (!reason.value.trim()) {
    message.value = "보완 사유를 입력하세요";
    return;
  }
  busy.value = true;
  try {
    await requestSupplement(props.itemId, reason.value.trim());
    emit("reviewed");
  } catch (e) {
    message.value = e instanceof Error ? e.message : "보완 지정에 실패했습니다";
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div v-if="visible" class="overlay" role="dialog" aria-modal="true" aria-label="검토 팝업">
    <div class="modal">
      <header>
        <h3>검토 팝업 <span class="version-tag">v{{ version.versionLabel }}</span></h3>
        <button class="close" aria-label="닫기" @click="emit('close')">✕</button>
      </header>

      <dl class="meta">
        <div><dt>파일명</dt><dd><a :href="version.downloadUrl">{{ version.fileName }}</a></dd></div>
        <div><dt>제출일시</dt><dd>{{ version.uploadedAt }}</dd></div>
        <div><dt>구분</dt><dd>{{ version.old ? "old(이전 버전)" : "최신본" }}</dd></div>
      </dl>
      <p class="download-hint">
        파일을 열람하려면 <a :href="version.downloadUrl">파일 다운로드</a>를 클릭하세요.
      </p>

      <label class="reason-label" for="review-reason">보완 내용</label>
      <input
        id="review-reason"
        ref="reasonInput"
        v-model="reason"
        placeholder="보완 사유(필수)"
        :disabled="busy"
      />

      <p v-if="message" class="msg">{{ message }}</p>

      <footer>
        <button class="danger" :disabled="busy" @click="doSupplement">보완 필요</button>
        <button class="success" :disabled="busy" @click="doConfirm">검토 완료</button>
        <button :disabled="busy" @click="emit('close')">닫기</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.4); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal { width: 460px; max-width: calc(100vw - 32px); background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-md); box-shadow: var(--shadow-card); padding: var(--space-4); }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-3); }
h3 { font: 700 var(--subtitle); margin: 0; }
.version-tag { background: var(--blue-50); color: var(--blue-600); padding: 2px 10px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 600; margin-left: 6px; vertical-align: middle; }
.close { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 4px 10px; cursor: pointer; color: var(--grey-700); }
.meta { margin: 0; display: grid; gap: var(--space-2); }
.meta div { display: flex; gap: var(--space-2); align-items: baseline; }
dt { min-width: 64px; color: var(--grey-500); font-size: 13px; }
dd { margin: 0; font-size: 15px; line-height: 22px; color: var(--grey-900); }
.meta a, .download-hint a { color: var(--blue-600); text-decoration: none; font-weight: 600; }
.download-hint { color: var(--grey-500); font-size: 13px; line-height: 20px; margin: var(--space-2) 0 var(--space-3); }
.reason-label { display: block; font: 600 15px/22px var(--font-sans); color: var(--grey-700); margin-bottom: var(--space-1); }
input { width: 100%; box-sizing: border-box; border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 8px 4px; font: 400 15px/22px var(--font-sans); color: var(--grey-900); background: transparent; outline: none; }
input::placeholder { color: var(--grey-500); }
input:focus { border-bottom-color: var(--blue-600); }
.msg { color: var(--blue-600); margin: var(--space-2) 0 0; font-size: 14px; }
footer { display: flex; gap: var(--space-2); margin-top: var(--space-4); flex-wrap: wrap; }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); }
button:disabled { opacity: 0.5; cursor: not-allowed; }
.primary { background: var(--blue-600); border-color: var(--blue-600); color: #ffffff; }
.danger { background: var(--red-600); border-color: var(--red-600); color: #ffffff; }
.success { background: var(--green-600); border-color: var(--green-600); color: #ffffff; }
</style>
