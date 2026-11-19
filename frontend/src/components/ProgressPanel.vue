<script setup lang="ts">
import type { PhaseProgress } from "../types/model";

defineProps<{ overallPct: number; confirmedCount: number; totalCount: number; phases: PhaseProgress[]; badges: string[] }>();
</script>

<template>
  <div class="panel">
    <div class="overall">
      <p class="label">산출물 완료율<span class="basis">검토완료 기준</span></p>
      <p class="pct">{{ overallPct }}<span class="unit">%</span></p>
      <div class="bar"><i :style="{ width: overallPct + '%' }"></i></div>
      <p class="complete-line">전체 {{ totalCount }}종 중 {{ confirmedCount }}종 완료</p>
      <div class="badges">
        <span v-for="badge in badges" :key="badge" class="badge">{{ badge }}</span>
      </div>
    </div>
    <div class="by-phase">
      <p class="label">단계별 완료율<span class="basis">검토완료 기준</span></p>
      <div v-for="phase in phases" :key="phase.name" class="phase-row">
        <span class="phase-name">{{ phase.name }}</span>
        <div class="phase-bar"><i :style="{ width: phase.pct + '%' }"></i></div>
        <span class="phase-pct">{{ phase.confirmed }}/{{ phase.total }} · {{ phase.pct }}%</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.panel { display: grid; grid-template-columns: 280px 1fr; gap: var(--space-8); background: var(--surface); border: 1px solid var(--grey-200); border-radius: var(--radius-lg); box-shadow: var(--shadow-card); padding: var(--space-6); }
.label { font: 600 15px/22px var(--font-sans); color: var(--grey-700); margin: 0 0 var(--space-2); }
.basis { margin-left: 8px; font: 400 12px/18px var(--font-sans); color: var(--grey-400); }
.pct { font: 700 var(--title1); color: var(--blue-600); margin: 0; }
.pct .unit { font-size: 16px; margin-left: 2px; color: var(--grey-500); }
.bar { width: 100%; height: 10px; background: var(--grey-100); border-radius: 5px; margin-top: var(--space-2); }
.bar i { display: block; height: 100%; background: var(--blue-600); border-radius: 5px; transition: width 0.3s ease; }
.complete-line { margin: var(--space-2) 0 0; font: 500 13px/20px var(--font-sans); color: var(--grey-600); }
.badges { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-top: var(--space-3); }
.badge { padding: 6px 12px; border-radius: 16px; font-size: 13px; font-weight: 600; background: var(--grey-100); color: var(--grey-700); }
.badge:nth-child(2) { background: var(--blue-50); color: var(--blue-600); }
.badge:nth-child(3) { background: var(--red-50); color: var(--red-600); }
.badge:nth-child(4) { background: var(--green-50); color: var(--green-600); }
.phase-row { display: grid; grid-template-columns: 120px 1fr 120px; align-items: center; gap: var(--space-3); margin-bottom: var(--space-2); }
.phase-name { font-size: 14px; line-height: 20px; color: var(--grey-900); font-weight: 600; }
.phase-bar { height: 10px; background: var(--grey-100); border-radius: 5px; overflow: hidden; }
.phase-bar i { display: block; height: 100%; background: var(--blue-600); border-radius: 5px; transition: width 0.3s ease; }
.phase-pct { font-size: 13px; line-height: 20px; color: var(--grey-500); text-align: right; }
@media (max-width: 900px) { .panel { grid-template-columns: 1fr; } }
</style>
