<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ status: string }>();

const statusClass = computed(() => {
  if (props.status === "검토완료") return "s-confirm";
  if (props.status === "검토 필요") return "s-review";
  if (props.status === "보완 필요") return "s-supplement";
  return "s-missing";
});
</script>

<template>
  <span class="st" :class="statusClass">{{ status }}</span>
</template>

<style scoped>
.st { display: inline-block; padding: 4px 12px; border-radius: var(--radius-sm); font-size: 13px; font-weight: 600; }
.s-review { background: var(--blue-50); color: var(--blue-600); }
.s-missing { background: var(--grey-100); color: var(--grey-700); }
.s-confirm { background: var(--green-50); color: var(--green-600); }
.s-supplement { background: var(--red-50); color: var(--red-600); }
</style>
