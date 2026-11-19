<script setup lang="ts">
import { ref } from "vue";
import Dashboard from "./views/Dashboard.vue";
import SubmissionInbox from "./views/SubmissionInbox.vue";
import DeliverableDetail from "./views/DeliverableDetail.vue";
import ReviewQueue from "./views/ReviewQueue.vue";

type Screen = "dashboard" | "inbox" | "reviewQueue" | "detail";

const screen = ref<Screen>("dashboard");
const selectedItemId = ref<number | null>(null);
/** 상세 화면으로 들어온 경로(뒤로 가기 때 되돌아갈 화면). */
const detailReturnScreen = ref<Exclude<Screen, "detail">>("dashboard");

function openInbox(): void {
  screen.value = "inbox";
}

function openReviewQueue(): void {
  screen.value = "reviewQueue";
}

function openDetail(itemId: number, from: Exclude<Screen, "detail"> = "dashboard"): void {
  selectedItemId.value = itemId;
  detailReturnScreen.value = from;
  screen.value = "detail";
}

function backToDashboard(): void {
  screen.value = "dashboard";
}

function backFromDetail(): void {
  screen.value = detailReturnScreen.value;
}
</script>

<template>
  <header class="topbar">
    <div class="brand" title="대시보드로 이동" @click="backToDashboard">
      <img class="mascot" src="./assets/character-ggumi.png" alt="꾸미 캐릭터" />
      <h1>프로젝트 산출물 검토 보드</h1>
    </div>
    <nav>
      <button :class="{ active: screen === 'dashboard' }" @click="backToDashboard">대시보드</button>
      <button :class="{ active: screen === 'inbox' }" @click="openInbox">제출 파일함</button>
      <button :class="{ active: screen === 'reviewQueue' }" @click="openReviewQueue">검토 파일 모음</button>
    </nav>
  </header>
  <main>
    <Dashboard v-if="screen === 'dashboard'" @open-detail="openDetail" @open-inbox="openInbox" />
    <SubmissionInbox v-else-if="screen === 'inbox'" @open-detail="openDetail" />
    <ReviewQueue v-else-if="screen === 'reviewQueue'" @open-detail="(id: number) => openDetail(id, 'reviewQueue')" />
    <DeliverableDetail v-else-if="screen === 'detail' && selectedItemId !== null" :item-id="selectedItemId" @back="backFromDetail" />
  </main>
</template>

<style>
* { box-sizing: border-box; }
body { font-family: var(--font-sans); margin: 0; background: var(--bg-page); color: var(--grey-900); line-height: 1.5; }
.topbar { display: flex; align-items: center; justify-content: space-between; background: var(--surface); color: var(--grey-900); padding: var(--space-4) var(--space-6); border-bottom: 1px solid var(--grey-200); }
.topbar h1 { font: 800 var(--title-brand) var(--font-sans); margin: 0; }
.topbar .brand { display: flex; align-items: center; gap: var(--space-2); cursor: pointer; }
.topbar .mascot { height: 44px; }
.topbar nav button { margin-left: var(--space-2); padding: 10px 16px; border: none; border-radius: var(--radius-sm); background: var(--grey-100); color: var(--grey-700); font: 600 15px/22px var(--font-sans); cursor: pointer; }
.topbar nav button.active { background: var(--blue-50); color: var(--blue-600); }
.topbar nav button:disabled { opacity: 0.5; cursor: not-allowed; }
main { padding: var(--space-6); }
</style>
