import { computed, ref } from "vue";
import { createProject as apiCreateProject, fetchDashboard, listProjects } from "../api/client";
import type { DashboardView, Project } from "../types/model";

/* 화면을 넘나드는 프로젝트 선택 상태(모듈 단일 상태) */
const projects = ref<Project[]>([]);
const selectedProjectId = ref<number | null>(null);
const dashboard = ref<DashboardView | null>(null);
const loading = ref(false);
const error = ref("");

const selectedProject = computed(
  () => projects.value.find((project) => project.id === selectedProjectId.value) ?? null,
);

async function loadProjects(): Promise<void> {
  projects.value = await listProjects();
  if (selectedProjectId.value === null && projects.value.length > 0) {
    selectedProjectId.value = projects.value[0].id;
  }
}

async function loadDashboard(): Promise<void> {
  if (selectedProjectId.value === null) {
    dashboard.value = null;
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    dashboard.value = await fetchDashboard(selectedProjectId.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "조회에 실패했습니다";
  } finally {
    loading.value = false;
  }
}

async function selectProject(): Promise<void> {
  await loadDashboard();
}

/** 프로젝트를 추가하고 목록을 다시 내려받아 방금 추가한 프로젝트 대시보드로 전환한다(REQ-outputhub-014). */
async function addProject(body: { name: string; vendor: string }): Promise<Project> {
  const project = await apiCreateProject(body);
  projects.value = await listProjects();
  selectedProjectId.value = project.id;
  await loadDashboard();
  return project;
}

export function useProjects() {
  return { projects, selectedProjectId, selectedProject, dashboard, loading, error, loadProjects, loadDashboard, selectProject, addProject };
}
