<script setup lang="ts">
import { onMounted, ref } from "vue";
import { getToken } from "../services/auth";

const emit = defineEmits<{
  navigate: [path: string];
}>();

const loading = ref(true);
const errorMessage = ref<string | null>(null);

const getPaymentsBase = () => {
  const apiBase =
    (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
    (import.meta.env.VITE_BACKEND_BASE_URL as string | undefined) ??
    "";
  const trimmed = apiBase.replace(/\/$/, "");
  return trimmed ? `${trimmed}/api/payments` : "/api/payments";
};

async function confirmPayment(paymentKey: string, orderId: string, amount: number) {
  const token = getToken();
  if (!token) {
    throw new Error("로그인이 만료되었습니다. 다시 로그인 후 결제를 확인해주세요.");
  }
  const response = await fetch(`${getPaymentsBase()}/confirm`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`
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
  const params = new URLSearchParams(window.location.search);
  const paymentKey = params.get("paymentKey") ?? "";
  const orderId = params.get("orderId") ?? "";
  const amount = Number(params.get("amount") ?? 0);

  if (!paymentKey || !orderId || !amount) {
    errorMessage.value = "결제 승인에 필요한 정보가 누락되었습니다.";
    loading.value = false;
    return;
  }

  try {
    const result = await confirmPayment(paymentKey, orderId, amount);

    // confirm 성공 → 완료 페이지 이동
    emit("navigate", `/concert/confirm?bookingId=${result.bookingId}`);
  } catch (error: any) {
    console.error("Confirm error:", error);

    emit(
      "navigate",
      `/payments/fail?code=CONFIRM_FAILED&message=${encodeURIComponent(
        error?.message ?? "결제 승인 처리 중 오류가 발생했습니다."
      )}&orderId=${encodeURIComponent(orderId)}`
    );
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
