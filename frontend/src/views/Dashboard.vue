<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  addProjectItem,
  deleteProjectItem,
  exportDashboardUrl,
  exportDeliverablesUrl,
} from "../api/client";
import AddProjectModal from "../components/AddProjectModal.vue";
import DeliverableSection from "../components/DeliverableSection.vue";
import ProgressPanel from "../components/ProgressPanel.vue";
import { useProjects } from "../composables/useProjects";
import type { ChecklistRow, PhaseProgress, Project } from "../types/model";

const emit = defineEmits<{ (e: "open-detail", itemId: number): void; (e: "open-inbox"): void }>();

const { projects, selectedProjectId, dashboard, loading, error, loadProjects, loadDashboard, selectProject } =
  useProjects();

const expandedPhases = ref<Record<string, boolean>>({});
const message = ref("");
const showAddProject = ref(false);

interface PhaseBucket {
  phase: PhaseProgress;
  rows: ChecklistRow[];
}

const phaseBuckets = computed<PhaseBucket[]>(() => {
  if (!dashboard.value) {
    return [];
  }
  const buckets = dashboard.value.phases.map((phase) => ({ phase, rows: [] as ChecklistRow[] }));
  const byName = new Map(buckets.map((bucket) => [bucket.phase.name, bucket]));
  for (const row of dashboard.value.rows) {
    let bucket = byName.get(row.phase);
    if (!bucket) {
      // 통계에서 제외된 단계(예: PMO 주간보고)는 목록에는 그대로 보여준다
      const excluded: PhaseProgress = { name: row.phase, total: 0, submitted: 0, confirmed: 0, pct: 0, submittedPct: 0, overdueCount: 0 };
      bucket = { phase: excluded, rows: [] };
      byName.set(row.phase, bucket);
      buckets.push(bucket);
    }
    bucket.rows.push(row);
  }
  return buckets;
});

/** 단계가 제출 통계 집계 대상인지(주간보고 등 누적 전용 단계는 제외) 판단한다. */
function isStatsPhase(phaseName: string): boolean {
  return dashboard.value?.phases.some((phase) => phase.name === phaseName) ?? false;
}

function isOpen(phaseName: string): boolean {
  return expandedPhases.value[phaseName] ?? true;
}

function togglePhase(phaseName: string): void {
  expandedPhases.value[phaseName] = !isOpen(phaseName);
}

async function onRemoveItem(itemId: number): Promise<void> {
  if (selectedProjectId.value === null) {
    return;
  }
  if (!window.confirm("이 산출물 항목을 삭제할까요? 연결된 제출 이력도 함께 정리됩니다.")) {
    return;
  }
  try {
    await deleteProjectItem(selectedProjectId.value, itemId);
    message.value = "산출물 항목을 삭제했습니다";
    await loadDashboard();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "삭제에 실패했습니다";
  }
}

async function onAddItem(body: { phase: string; itemName: string; required: boolean; dueDate: string | null }): Promise<void> {
  if (selectedProjectId.value === null) {
    return;
  }
  try {
    await addProjectItem(selectedProjectId.value, body);
    message.value = "산출물 항목을 추가했습니다";
    await loadDashboard();
  } catch (e) {
    message.value = e instanceof Error ? e.message : "추가에 실패했습니다";
  }
}

function openAddProject(): void {
  showAddProject.value = true;
}

function onProjectCreated(project: Project): void {
  showAddProject.value = false;
  message.value = `${project.name} 프로젝트를 추가했습니다. 기본 산출물 목록과 대시보드가 세팅되었습니다`;
}

onMounted(async () => {
  await loadProjects();
  await loadDashboard();
});
</script>

<template>
  <section class="dashboard">
    <div class="toolbar">
      <select v-model.number="selectedProjectId" @change="selectProject">
        <option v-for="project in projects" :key="project.id" :value="project.id">
          {{ project.name }}
        </option>
      </select>
      <button @click="openAddProject">+ 프로젝트 추가</button>
      <button class="primary" @click="emit('open-inbox')">+ 파일 업로드</button>
      <a :href="selectedProjectId ? exportDashboardUrl(selectedProjectId) : '#'" class="button">대시보드 내보내기</a>
      <a :href="selectedProjectId ? exportDeliverablesUrl(selectedProjectId) : '#'" class="button">산출물 다운로드</a>
    </div>

    <p v-if="loading">조회 중…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!dashboard" class="empty">프로젝트가 없습니다. 관리자에게 기본 세팅을 요청하세요.</p>

    <template v-else>
      <header class="head">
        <div>
          <h2>{{ dashboard.project.name }}</h2>
          <p class="sub">수행사: {{ dashboard.project.vendor }} · PMO 산출물 검토 현황</p>
        </div>
      </header>

      <ProgressPanel
        :overall-pct="dashboard.overallPct"
        :confirmed-count="dashboard.confirmedCount"
        :total-count="dashboard.totalCount"
        :phases="dashboard.phases"
        :badges="dashboard.badges"
      />

      <p v-if="message" class="message">{{ message }}</p>

      <h3 class="list-title">최종산출물 목록</h3>
      <div class="sections">
        <DeliverableSection
          v-for="bucket in phaseBuckets"
          :key="bucket.phase.name"
          :phase="bucket.phase.name"
          :total="bucket.phase.total"
          :submitted="bucket.phase.submitted"
          :submitted-pct="bucket.phase.submittedPct"
          :confirmed="bucket.phase.confirmed"
          :pct="bucket.phase.pct"
          :rows="bucket.rows"
          :expanded="isOpen(bucket.phase.name)"
          :show-stats="isStatsPhase(bucket.phase.name)"
          :allow-add-item="bucket.phase.name !== 'PMO'"
          :overdue-count="bucket.phase.overdueCount"
          @toggle="togglePhase(bucket.phase.name)"
          @open-detail="(id: number) => emit('open-detail', id)"
          @remove-item="onRemoveItem"
          @add-item="onAddItem"
        />
      </div>
    </template>

    <AddProjectModal
      :visible="showAddProject"
      @close="showAddProject = false"
      @created="onProjectCreated"
    />
  </section>
</template>

<style scoped>
.toolbar { display: flex; gap: var(--space-2); align-items: center; margin-bottom: var(--space-4); }
.toolbar select { padding: 10px 16px; border: 1px solid var(--grey-200); border-radius: var(--radius-sm); background: var(--surface); font: 600 15px/22px var(--font-sans); color: var(--grey-900); }
button, .button { border: 1px solid var(--grey-200); background: var(--surface); border-radius: var(--radius-sm); padding: 10px 16px; font: 600 15px/22px var(--font-sans); cursor: pointer; color: var(--grey-700); text-decoration: none; }
.primary { background: var(--blue-600); border-color: var(--blue-600); color: #ffffff; }
.primary:hover { background: var(--blue-700); border-color: var(--blue-700); }
.head { margin-bottom: var(--space-3); }
.head h2 { font: 700 var(--title2); margin: 0; }
.sub { color: var(--grey-500); font-size: 13px; line-height: 20px; }
.list-title { font: 700 var(--subtitle); margin: var(--space-6) 0 var(--space-3); }
.sections { display: flex; flex-direction: column; gap: var(--space-3); }
.message { color: var(--blue-600); margin: var(--space-3) 0 0; }
.error { color: var(--red-600); }
.empty { padding: var(--space-6); background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-md); box-shadow: var(--shadow-card); color: var(--grey-500); }
</style>
