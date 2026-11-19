<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { useProjects } from "../composables/useProjects";
import type { Project } from "../types/model";

const props = defineProps<{ visible: boolean }>();
const emit = defineEmits<{
  (e: "close"): void;
  (e: "created", project: Project): void;
}>();

/** 기본 세팅의 기본 수행사(ProjectInitializer 와 같은 값) */
const DEFAULT_VENDOR = "미지정";

const { addProject } = useProjects();

const name = ref("");
const vendor = ref("");
const validationMessage = ref("");
const serverError = ref("");
const busy = ref(false);
const nameInput = ref<HTMLInputElement | null>(null);

watch(
  () => props.visible,
  async (open) => {
    if (!open) return;
    name.value = "";
    vendor.value = "";
    validationMessage.value = "";
    serverError.value = "";
    await nextTick();
    nameInput.value?.focus();
  },
);

/** 이름은 서버 중복 검사 전에 여기서 먼저 확인하고, 수행사는 비우면 기본값으로 보낸다 */
async function submit(): Promise<void> {
  if (!name.value.trim()) {
    validationMessage.value = "프로젝트 이름을 입력하세요";
    return;
  }
  busy.value = true;
  serverError.value = "";
  try {
    const project = await addProject({ name: name.value.trim(), vendor: vendor.value.trim() || DEFAULT_VENDOR });
    emit("created", project);
  } catch (e) {
    serverError.value = e instanceof Error ? e.message : "추가에 실패했습니다";
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div v-if="visible" class="overlay" role="dialog" aria-modal="true" aria-label="프로젝트 추가">
    <form class="modal" @submit.prevent="submit">
      <header>
        <h3>프로젝트 추가</h3>
        <button type="button" class="close" aria-label="닫기" @click="emit('close')">✕</button>
      </header>

      <p class="hint">
        추가하면 기본 세팅과 같은 산출물 목록(착수 ~ PMO)과 대시보드가 자동으로 만들어집니다.
      </p>

      <label class="field-label" for="project-name">프로젝트 이름</label>
      <input
        id="project-name"
        ref="nameInput"
        v-model="name"
        placeholder="예: 2026-002 Day1 계약 품의"
        :disabled="busy"
      />

      <label class="field-label" for="project-vendor">수행사</label>
      <input
        id="project-vendor"
        v-model="vendor"
        :placeholder="DEFAULT_VENDOR"
        :disabled="busy"
      />

      <p v-if="validationMessage || serverError" class="msg">{{ validationMessage || serverError }}</p>

      <footer>
        <button type="button" :disabled="busy" @click="emit('close')">취소</button>
        <button type="submit" class="primary" :disabled="busy">
          {{ busy ? "추가 중…" : "추가" }}
        </button>
      </footer>
    </form>
  </div>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.4); display: flex; align-items: center; justify-content: center; z-index: 50; }
.modal { width: 460px; max-width: calc(100vw - 32px); background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-md); box-shadow: var(--shadow-card); padding: var(--space-4); }
header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
h3 { font: 700 var(--subtitle); margin: 0; }
.close { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 4px 10px; cursor: pointer; color: var(--grey-700); }
.hint { color: var(--grey-500); font-size: 13px; line-height: 20px; margin: 0 0 var(--space-3); }
.field-label { display: block; font: 600 15px/22px var(--font-sans); color: var(--grey-700); margin: var(--space-3) 0 var(--space-1); }
input { width: 100%; box-sizing: border-box; border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 8px 4px; font: 400 15px/22px var(--font-sans); color: var(--grey-900); background: transparent; outline: none; }
input::placeholder { color: var(--grey-500); }
input:focus { border-bottom-color: var(--blue-600); }
.msg { color: var(--red-600); margin: var(--space-3) 0 0; font-size: 14px; }
footer { display: flex; gap: var(--space-2); margin-top: var(--space-4); flex-wrap: wrap; justify-content: flex-end; }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); }
button:disabled { opacity: 0.5; cursor: not-allowed; }
.primary { background: var(--blue-600); border-color: var(--blue-600); color: #ffffff; }
.primary:hover { background: var(--blue-700); border-color: var(--blue-700); }
</style>
