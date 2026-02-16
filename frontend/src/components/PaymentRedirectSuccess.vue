<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const errorMessage = ref<string | null>(null);

async function confirmPayment(paymentKey: string, orderId: string, amount: number) {
  const response = await fetch("/api/payments/confirm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // 필요하면 X-USER-ID 헤더 추가
    },
    body: JSON.stringify({
      paymentKey,
      orderId,
      amount,
    }),
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(text || `Confirm failed (${response.status})`);
  }

  return await response.json();
}

onMounted(async () => {
  const paymentKey = String(route.query.paymentKey ?? "");
  const orderId = String(route.query.orderId ?? "");
  const amount = Number(route.query.amount ?? 0);

  if (!paymentKey || !orderId || !amount) {
    errorMessage.value = "결제 승인에 필요한 정보가 누락되었습니다.";
    loading.value = false;
    return;
  }

  try {
    const result = await confirmPayment(paymentKey, orderId, amount);

    // confirm 성공 → 완료 페이지 이동
    router.replace({
      path: "/booking/complete",
      query: {
        bookingId: result.bookingId,
      },
    });

  } catch (error: any) {
    console.error("Confirm error:", error);

    router.replace({
      path: "/payments/fail",
      query: {
        code: "CONFIRM_FAILED",
        message: error?.message ?? "결제 승인 처리 중 오류가 발생했습니다.",
        orderId,
      },
    });
  }
});
</script>

<template>
  <div class="mx-auto mt-24 max-w-[560px] text-center">
    <div v-if="loading">
      <h2 class="text-xl font-bold text-[#333]">결제 확인 중...</h2>
      <p class="mt-2 text-sm text-[#666]">
        잠시만 기다려주세요.
      </p>
    </div>

    <div v-if="errorMessage">
      <h2 class="text-xl font-bold text-red-600">오류 발생</h2>
      <p class="mt-2 text-sm text-[#666]">
        {{ errorMessage }}
      </p>
    </div>
  </div>
</template>
