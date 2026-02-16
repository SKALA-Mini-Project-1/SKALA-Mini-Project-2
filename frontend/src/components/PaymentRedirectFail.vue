<script setup lang="ts">
import { computed } from "vue";

const emit = defineEmits<{
  navigate: [path: string];
}>();

const params = new URLSearchParams(window.location.search);
const code = computed(() => String(params.get("code") ?? "PAYMENT_FAILED"));
const message = computed(() => String(params.get("message") ?? "결제가 실패했습니다."));
const orderId = computed(() => String(params.get("orderId") ?? ""));
</script>

<template>
  <div style="max-width: 560px; margin: 80px auto; text-align: center;">
    <h2>결제 실패</h2>
    <p style="margin-top: 12px;">{{ message }}</p>

    <div style="margin-top: 12px; font-size: 14px; opacity: 0.8;">
      <div>code: {{ code }}</div>
      <div v-if="orderId">orderId: {{ orderId }}</div>
    </div>

    <div style="margin-top: 20px; display: flex; gap: 8px; justify-content: center;">
      <button @click="emit('navigate', '/concert/payment')">다시 결제하기</button>
      <button @click="emit('navigate', '/concert/seat')">좌석 선택으로</button>
    </div>
  </div>
</template>
