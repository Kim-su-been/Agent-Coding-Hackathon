<script setup lang="ts">
import { computed, onMounted } from "vue";
import StatusBadge from "../components/StatusBadge.vue";
import { useProjects } from "../composables/useProjects";
import type { ChecklistRow } from "../types/model";

const emit = defineEmits<{ (e: "open-detail", itemId: number): void }>();

const { projects, selectedProjectId, dashboard, loading, error, loadProjects, loadDashboard, selectProject } =
  useProjects();

/** 검토 대기(검토 필요) 상태로 최신 제출본이 있는 대장 행만 모은다(REQ-outputhub-027). */
const pendingReviewRows = computed<ChecklistRow[]>(() =>
  (dashboard.value?.rows ?? []).filter((row) => row.status === "검토 필요" && row.latestDownloadUrl !== null),
);

onMounted(async () => {
  await loadProjects();
  await loadDashboard();
});
</script>

<template>
  <section class="queue">
    <div class="toolbar">
      <select v-model.number="selectedProjectId" aria-label="프로젝트 선택" @change="selectProject">
        <option v-for="project in projects" :key="project.id" :value="project.id">
          {{ project.name }}
        </option>
      </select>
      <span class="count">검토 대기 {{ pendingReviewRows.length }}건</span>
    </div>

    <p v-if="loading">조회 중…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!dashboard" class="empty">프로젝트가 없습니다. 관리자에게 기본 세팅을 요청하세요.</p>

    <template v-else>
      <h3 class="list-title">검토 대기 파일</h3>
      <p class="hint">
        검토 필요(검토 대기) 항목의 최신 제출 파일만 모아 보여줍니다. 보완 필요·검토완료 항목은 대시보드에서 확인하세요
      </p>
      <table>
        <thead>
          <tr>
            <th scope="col">단계</th>
            <th scope="col">산출물 항목</th>
            <th scope="col">상태</th>
            <th scope="col">버전</th>
            <th scope="col">제출 파일</th>
            <th scope="col">동작</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in pendingReviewRows" :key="row.id">
            <td>{{ row.phase }}</td>
            <td>{{ row.itemName }}</td>
            <td><StatusBadge :status="row.status" /></td>
            <td>{{ row.latestVersionLabel ? "v" + row.latestVersionLabel : "—" }}</td>
            <td>
              <a v-if="row.latestDownloadUrl" :href="row.latestDownloadUrl">{{ row.latestFileName }}</a>
              <span v-else class="muted">—</span>
            </td>
            <td><button @click="emit('open-detail', row.id)">상세</button></td>
          </tr>
          <tr v-if="pendingReviewRows.length === 0">
            <td colspan="6" class="muted">검토 대기 파일이 없습니다</td>
          </tr>
        </tbody>
      </table>
    </template>
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: var(--space-3); align-items: center; margin-bottom: var(--space-4); }
.toolbar select { padding: 10px 16px; border: 1px solid var(--grey-200); border-radius: var(--radius-sm); background: var(--surface); font: 600 15px/22px var(--font-sans); color: var(--grey-900); }
.count { font: 700 var(--subtitle); color: var(--grey-900); }
.list-title { font: 700 var(--subtitle); margin: 0 0 var(--space-1); }
.hint { color: var(--grey-500); font-size: 13px; line-height: 20px; margin: 0 0 var(--space-3); }
table { width: 100%; border-collapse: collapse; background: var(--surface); font-size: 15px; line-height: 22px; box-shadow: var(--shadow-card); }
th, td { text-align: left; padding: 12px 16px; border-bottom: 1px solid var(--grey-200); }
th { background: var(--grey-50); color: var(--grey-700); font-weight: 600; }
td a { color: var(--blue-600); text-decoration: none; }
button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 8px 14px; font: 600 14px/20px var(--font-sans); cursor: pointer; color: var(--grey-700); }
.muted { color: var(--grey-400); }
.error { color: var(--red-600); }
.empty { padding: var(--space-6); background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-md); box-shadow: var(--shadow-card); color: var(--grey-500); }
</style>
