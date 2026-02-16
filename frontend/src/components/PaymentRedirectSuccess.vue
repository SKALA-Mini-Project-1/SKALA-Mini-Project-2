<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

const errorMessage = ref<string>("");

async function confirmPayment(paymentKey: string, orderId: string, amount: number) {
  // TODO: 백엔드 confirm API 경로는 너희 스프링 엔드포인트에 맞춰 수정
  // 예: POST /payments/confirm 또는 POST /payments/{paymentId}/confirm
  const res = await fetch("/api/payments/confirm", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // TODO: 인증/유저 식별 필요하면 여기 추가 (X-USER-ID 등)
    },
    body: JSON.stringify({ paymentKey, orderId, amount }),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `confirm failed: ${res.status}`);
  }

  return await res.json();
}

onMounted(async () => {
  const paymentKey = String(route.query.paymentKey ?? "");
  const orderId = String(route.query.orderId ?? "");
  const amount = Number(route.query.amount ?? 0);

  if (!paymentKey || !orderId || !amount) {
    errorMessage.value = "결제 승인에 필요한 값이 누락되었습니다.";
    return;
  }

  try {
    const result = await confirmPayment(paymentKey, orderId, amount);

    // result에 bookingId 같은 걸 내려주면 완료 페이지에서 조회/표시에 활용 가능
    // 예: router.replace(`/booking/complete?bookingId=${result.bookingId}`)
    router.replace("/booking/complete");
  } catch (e: any) {
    // confirm 실패면 실패 페이지로 넘겨서 안내
    router.replace({
      path: "/payments/fail",
      query: {
        code: "CONFIRM_FAILED",
        message: e?.message ?? "결제 승인 처리 중 오류가 발생했습니다.",
        orderId,
      },
    });
  }
});
</script>

<template>
  <div style="max-width: 560px; margin: 80px auto; text-align: center;">
    <h2>결제 확인 중...</h2>
    <p v-if="!errorMessage">잠시만 기다려주세요.</p>

    <div v-if="errorMessage" style="margin-top: 16px;">
      <p>{{ errorMessage }}</p>
      <button @click="$router.replace('/payments/fail')">확인</button>
    </div>
  </div>
</template>
