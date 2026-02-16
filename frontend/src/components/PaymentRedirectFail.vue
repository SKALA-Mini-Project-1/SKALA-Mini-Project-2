<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const code = computed(() => String(route.query.code ?? "PAYMENT_FAILED"));
const message = computed(() => String(route.query.message ?? "결제가 실패했습니다."));
const orderId = computed(() => String(route.query.orderId ?? ""));
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
      <button @click="router.replace('/payments')">다시 결제하기</button>
      <button @click="router.replace('/seats')">좌석 선택으로</button>
    </div>
  </div>
</template>
