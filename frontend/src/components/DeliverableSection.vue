<script setup lang="ts">
import { ref } from "vue";
import StatusBadge from "./StatusBadge.vue";
import type { ChecklistRow } from "../types/model";

const props = withDefaults(
  defineProps<{
    phase: string;
    total: number;
    submitted: number;
    submittedPct: number;
    confirmed: number;
    pct: number;
    rows: ChecklistRow[];
    expanded: boolean;
    /** 접수 단계 헤더에 제출 통계를 보여줄지(주간보고 등 누적 전용 단계는 숨김) */
    showStats?: boolean;
    /** 이 단계에서 산출물 항목 추가를 허용할지(PMO 주간보고는 제외) */
    allowAddItem?: boolean;
  }>(),
  { showStats: true, allowAddItem: true },
);

const emit = defineEmits<{
  (e: "toggle"): void;
  (e: "open-detail", itemId: number): void;
  (e: "remove-item", itemId: number): void;
  (e: "add-item", body: { phase: string; itemName: string; required: boolean }): void;
}>();

const newItemName = ref("");
const newItemRequired = ref(false);
const adding = ref(false);

function toggleForm(): void {
  adding.value = !adding.value;
}

function submitAdd(): void {
  const name = newItemName.value.trim();
  if (!name) {
    return;
  }
  emit("add-item", { phase: props.phase, itemName: name, required: newItemRequired.value });
  newItemName.value = "";
  newItemRequired.value = false;
  adding.value = false;
}
</script>

<template>
  <section class="section">
    <button class="head" @click="emit('toggle')">
      <span class="chevron" :class="{ open: expanded }">▶</span>
      <strong class="name">{{ phase }}</strong>
      <template v-if="props.showStats">
        <span class="count">제출 {{ submitted }}/{{ total }} · {{ submittedPct }}%</span>
        <span class="count">완료 {{ confirmed }}/{{ total }} · {{ pct }}%</span>
        <div class="mini-bar"><i :style="{ width: pct + '%' }"></i></div>
      </template>
    </button>

    <div v-if="expanded" class="body">
      <table>
        <thead>
          <tr>
            <th>산출물 항목</th><th>상태</th><th>제출문서</th><th>버전</th><th>동작</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td>
              {{ row.itemName }}
              <span v-if="row.supplementReason" class="reason">사유: {{ row.supplementReason }}</span>
            </td>
            <td><StatusBadge :status="row.status" /></td>
            <td>
              <!-- REQ-outputhub-023: 항목별 최신본 1개만 링크로 표시. 주간보고 누적 항목도 예외 없음 -->
              <a v-if="row.latestDownloadUrl" :href="row.latestDownloadUrl">{{ row.latestFileName }}</a>
              <span v-else class="muted">—</span>
            </td>
            <td>{{ row.latestVersionLabel ? "v" + row.latestVersionLabel : "—" }}</td>
            <td class="actions">
              <button @click="emit('open-detail', row.id)">상세</button>
              <button class="danger" @click="emit('remove-item', row.id)">삭제</button>
            </td>
          </tr>
          <tr v-if="rows.length === 0">
            <td colspan="5" class="muted">등록된 산출물이 없습니다</td>
          </tr>
        </tbody>
      </table>

      <div v-if="props.allowAddItem" class="add-area">
        <button v-if="!adding" class="add-toggle" @click="toggleForm">+ 산출물 항목 추가</button>
        <div v-else class="add-form">
          <input v-model="newItemName" placeholder="산출물 이름 (예: 품의서)" @keyup.enter="submitAdd" />
          <label class="required-check">
            <input v-model="newItemRequired" type="checkbox" /> 필수
          </label>
          <button class="primary" @click="submitAdd">추가</button>
          <button @click="toggleForm">취소</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.section { background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-lg); box-shadow: var(--shadow-card); overflow: hidden; }
.head { display: flex; align-items: center; gap: var(--space-3); width: 100%; padding: var(--space-4) var(--space-5); background: var(--surface); border: none; cursor: pointer; text-align: left; }
.head:hover { background: var(--grey-50); }
.chevron { font-size: 12px; color: var(--grey-500); transition: transform 0.2s ease; }
.chevron.open { transform: rotate(90deg); }
.name { font: 700 var(--subtitle); color: var(--grey-900); }
.count { font-size: 13px; line-height: 20px; color: var(--grey-500); white-space: nowrap; }
.mini-bar { flex: 1; height: 8px; background: var(--grey-100); border-radius: 4px; overflow: hidden; }
.mini-bar i { display: block; height: 100%; background: var(--blue-600); border-radius: 4px; }
.body { border-top: 1px solid var(--grey-200); }
table { width: 100%; border-collapse: collapse; font-size: 15px; line-height: 22px; }
th { text-align: left; background: var(--grey-50); padding: 12px 16px; border-bottom: 1px solid var(--grey-200); color: var(--grey-700); font-weight: 600; }
td { padding: 12px 16px; border-bottom: 1px solid var(--grey-200); vertical-align: top; }
td a { color: var(--blue-600); text-decoration: none; }
.reason { display: block; color: var(--red-600); font-size: 13px; line-height: 20px; margin-top: var(--space-1); }
.file-link { display: block; }
.actions { white-space: nowrap; }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 8px 14px; font: 600 14px/20px var(--font-sans); cursor: pointer; color: var(--grey-700); }
button.danger:hover { border-color: var(--red-600); color: var(--red-600); }
.primary { background: var(--blue-600); border-color: var(--blue-600); color: #ffffff; }
.primary:hover { background: var(--blue-700); border-color: var(--blue-700); }
.add-area { padding: var(--space-3) var(--space-5) var(--space-4); }
.add-toggle { border-style: dashed; width: 100%; }
.add-form { display: flex; gap: var(--space-2); align-items: center; flex-wrap: wrap; }
.add-form input { border: none; border-bottom: 2px solid var(--grey-200); border-radius: 0; padding: 8px 4px; font: 400 15px/22px var(--font-sans); color: var(--grey-900); background: transparent; outline: none; min-width: 260px; }
.add-form input::placeholder { color: var(--grey-500); }
.add-form input:focus { border-bottom-color: var(--blue-600); }
.required-check { font-size: 14px; color: var(--grey-700); display: flex; align-items: center; gap: 6px; }
.muted { color: var(--grey-400); }
</style>
