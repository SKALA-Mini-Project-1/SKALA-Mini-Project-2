<!-- <script setup lang="ts">
import { Loader2 } from 'lucide-vue-next';
import { computed, reactive, ref } from 'vue';
import type { BookingData } from '../types';

const API_BASE = "http://localhost:8081"; // 나중에 배포 환경이면 환경변수(VITE_API_BASE)로 분리 권장


const props = defineProps<{
  bookingData: BookingData;
}>();

const emit = defineEmits<{
  paymentComplete: [];
}>();

const isProcessing = ref(false);
const paymentMethod = ref('card');
const agreements = reactive({
  all: false,
  terms: false,
  privacy: false,
  refund: false
});

const totalAmount = computed(() => props.bookingData.seats.reduce((sum, seat) => sum + seat.price, 0));

const updateAllFlag = () => {
  agreements.all = agreements.terms && agreements.privacy && agreements.refund;
};

const toggleAll = () => {
  const next = !agreements.all;
  agreements.all = next;
  agreements.terms = next;
  agreements.privacy = next;
  agreements.refund = next;
};

// 결제 버튼 클릭 → 결제 생성/검증 → Toss 위젯 호출 흐름
const handlePayment = async () => {
  // 약관 미동의 시 결제 진행 금지 (현재 UI와 동일한 정책)
  if (!agreements.terms || !agreements.privacy || !agreements.refund) return;

  isProcessing.value = true;

  try {
    // =========================
    // 0) 환경변수 로드
    // =========================
    const tossClientKey = import.meta.env.VITE_TEST_CLIENT_KEY as string;

    // TODO(나중에 수정): 아래 3개는 "테스트용 고정값"
    // - 실제 서비스에서는 bookingId/seatId는 "좌석 선택/예약 생성"에서 받아와야 함
    // - userId는 인증(JWT/세션)으로부터 얻고, X-USER-ID 헤더도 제거/대체(@AuthenticationPrincipal 등)
    const bookingId = import.meta.env.VITE_TEST_BOOKING_ID as string;
    const userId = import.meta.env.VITE_TEST_USER_ID as string;
    const seatId = import.meta.env.VITE_TEST_SEAT_ID as string;

    if (!tossClientKey) throw new Error("VITE_TEST_CLIENT_KEY missing");
    if (!bookingId || !userId || !seatId) throw new Error("VITE_TEST_* UUID missing");

    // =========================
    // 1) 결제 금액 확정
    // =========================
    // TODO(나중에 수정): 지금은 화면의 seats 합계를 그대로 사용
    // - 실제 서비스에서는 서버에서 확정된 금액(Price/Discount/Fees 포함)을 기준으로 결제 생성/검증 권장
    const total = Number(totalAmount.value);

    if (!Number.isFinite(total) || total <= 0) {
      throw new Error("Invalid total amount");
    }

    // =========================
    // 2) create (bookingId 기반 create-or-get)
    // =========================
    // TODO(나중에 수정): booking 검증/좌석 홀드/예약 생성 흐름이 붙으면
    // - create 호출 위치가 "결제 페이지 진입 시점" 또는 "좌석 확정 직후"로 바뀔 수 있음
    // - create 요청 바디 필드명은 백엔드 PaymentCreateRequest와 반드시 일치해야 함
    const createRes = await fetch(`${API_BASE}/payments/create`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        bookingId,
        userId: Number(userId),   // TODO(나중에 수정): 인증 도입 시 제거 가능(서버에서 인증정보로 추론)
        seatId: Number(seatId),   // TODO(나중에 수정): 여러 좌석이면 seatIds 배열 등으로 변경 필요
        amount: total
      })
    });

    if (!createRes.ok) {
      // 디버깅 편의: 백엔드 에러 바디를 그대로 출력
      const txt = await createRes.text();
      throw new Error(`payments/create failed: ${txt}`);
    }

    const created = await createRes.json();

    // TODO(나중에 수정): 응답 필드명이 id가 아니라 paymentId 등으로 바뀌면 여기 수정
    const paymentId = created.id as string;
    if (!paymentId) throw new Error("paymentId missing from create response");

    // =========================
    // 3) submit (결제 보호 시작: PENDING → PAYING, TTL 연장)
    // =========================
    // TODO(나중에 수정): 현재는 X-USER-ID 헤더로 userId를 전달
    // - 인증 도입 시 이 헤더는 제거하고 서버가 인증 토큰에서 userId를 꺼내도록 변경
    const submitRes = await fetch(`${API_BASE}/payments/${paymentId}/submit`, {
      method: "POST",
      headers: { "X-USER-ID": userId }
    });

    if (!submitRes.ok) {
      const txt = await submitRes.text();
      throw new Error(`payments/submit failed: ${txt}`);
    }

    const submit = await submitRes.json();

    // TODO(나중에 수정): submit 응답 필드명 변경 가능성에 대비
    // - 현재 백엔드 PaymentSubmitResponse 기준: amount, orderId, orderName, customerKey, successUrl, failUrl
    if (!submit?.orderId || !submit?.successUrl || !submit?.failUrl) {
      throw new Error("Invalid submit response");
    }

    // =========================
    // 4) Toss 위젯 호출
    // =========================
    // TODO(나중에 수정): 결제수단 선택 로직 연동
    // - 지금은 "카드" 고정
    // - paymentMethod 상태값(card/toss/simple/vbank)과 매핑해서 requestPayment의 method를 바꿔야 함
    const { loadTossPayments } = await import("@tosspayments/payment-sdk");
    const tossPayments = await loadTossPayments(tossClientKey);

    await tossPayments.requestPayment("카드", {
      amount: submit.amount,          // 서버가 확정한 금액 사용
      orderId: submit.orderId,
      orderName: submit.orderName,
      successUrl: submit.successUrl,  // 백엔드: /payments/toss/success
      failUrl: submit.failUrl         // 백엔드: /payments/toss/fail
    });

    // 위젯 결제 성공 시 브라우저가 successUrl로 이동하면서 이 함수 흐름은 사실상 끊김(리다이렉트)
    // 성공/실패 결과 화면은 /payments/result 라우트에서 처리

  } finally {
    // 위젯 리다이렉트가 일어나면 아래가 실행되기 전에 페이지가 이동할 수 있음
    isProcessing.value = false;
  }
};
</script>

<template>
  <div v-if="isProcessing" class="flex min-h-[600px] flex-col items-center justify-center bg-white">
    <Loader2 :size="48" class="mb-4 animate-spin text-[#FF6B00]" />
    <h2 class="text-xl font-bold text-[#333]">결제 처리 중입니다...</h2>
    <p class="mt-2 text-[#666]">잠시만 기다려주세요. 화면을 닫지 마세요.</p>
  </div>

  <div v-else class="mx-auto max-w-[1000px] p-3 sm:p-4 md:p-8">
    <h2 class="mb-6 border-b-2 border-[#333] pb-2 text-2xl font-bold text-[#333]">결제하기</h2>

    <div class="flex flex-col gap-8 lg:flex-row">
      <div class="flex-1 space-y-8">
        <section>
          <h3 class="mb-3 flex items-center text-lg font-bold text-[#333]"><span class="mr-2 h-4 w-1 bg-[#FF6B00]"></span>예매자 정보</h3>
          <div class="border-t border-[#333]">
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row"><div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">이름</div><div class="flex-1 p-3"><input type="text" class="w-full border border-[#ddd] px-3 py-2 text-sm sm:max-w-xs" placeholder="홍길동" /></div></div>
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row"><div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">연락처</div><div class="flex flex-1 flex-col gap-2 p-3 sm:flex-row sm:items-center sm:space-x-2 sm:gap-0"><input type="text" class="w-full border border-[#ddd] px-3 py-2 text-sm sm:max-w-xs" placeholder="010-0000-0000" /><button class="rounded-sm bg-[#666] px-3 py-2 text-xs text-white hover:bg-[#555]">인증요청</button></div></div>
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row"><div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">이메일</div><div class="flex-1 p-3"><input type="email" class="w-full border border-[#ddd] px-3 py-2 text-sm sm:max-w-xs" placeholder="example@email.com" /></div></div>
          </div>
        </section>

        <section>
          <h3 class="mb-3 flex items-center text-lg font-bold text-[#333]"><span class="mr-2 h-4 w-1 bg-[#FF6B00]"></span>결제 수단</h3>
          <div class="rounded-sm border border-[#e0e0e0] p-4">
            <div class="mb-4 grid grid-cols-2 gap-2 border-b border-[#e0e0e0] pb-4 sm:flex sm:space-x-2 sm:gap-0">
              <button
                v-for="method in ['card', 'toss', 'simple', 'vbank']"
                :key="method"
                class="flex-1 rounded-sm border py-3 text-sm font-bold transition-all"
                :class="paymentMethod === method ? 'border-[#FF6B00] bg-orange-50 text-[#FF6B00]' : 'border-[#ddd] bg-white text-[#666] hover:bg-gray-50'"
                @click="paymentMethod = method"
              >
                {{ method === 'card' ? '신용카드' : method === 'toss' ? '토스페이' : method === 'simple' ? '간편결제' : '무통장입금' }}
              </button>
            </div>

            <div class="min-h-[150px] p-2">
              <div v-if="paymentMethod === 'card'" class="space-y-3">
                <select class="w-full border border-[#ddd] p-2 text-sm"><option>카드사 선택</option><option>현대카드</option><option>삼성카드</option><option>신한카드</option></select>
                <div class="flex flex-col gap-2 sm:flex-row sm:space-x-2 sm:gap-0"><input type="text" placeholder="카드번호 16자리" class="flex-1 border border-[#ddd] p-2 text-sm" /><input type="text" placeholder="CVC" class="w-full border border-[#ddd] p-2 text-sm sm:w-20" /></div>
              </div>
              <div v-else-if="paymentMethod === 'toss'" class="flex h-full flex-col items-center justify-center py-4"><div class="mb-2 rounded-full bg-blue-500 px-6 py-2 font-bold text-white">Toss</div><p class="text-sm text-[#666]">토스 앱으로 간편하게 결제하세요.</p></div>
            </div>
          </div>
        </section>
      </div>

      <div class="lg:w-[340px]">
        <div class="rounded-sm border-2 border-[#333] bg-white p-4 sm:p-6 lg:sticky lg:top-24">
          <h3 class="mb-4 border-b border-[#eee] pb-2 text-lg font-bold">결제 정보</h3>

          <div class="mb-6 space-y-4">
            <div><div class="mb-1 text-xs text-[#999]">공연명</div><div class="font-bold text-[#333]">{{ bookingData.concertTitle || '선택한 공연' }}</div></div>
            <div><div class="mb-1 text-xs text-[#999]">일시</div><div class="text-sm text-[#333]">{{ bookingData.date }} {{ bookingData.session }}회차</div></div>
            <div><div class="mb-1 text-xs text-[#999]">장소</div><div class="text-sm text-[#333]">{{ bookingData.concertVenue || '-' }}</div></div>
            <div>
              <div class="mb-1 text-xs text-[#999]">선택좌석</div>
              <div class="space-y-1 text-sm text-[#333]">
                <div v-for="seat in bookingData.seats" :key="seat.id" class="flex justify-between">
                  <span>{{ seat.grade }} {{ seat.section }}구역 {{ seat.row }}열 {{ seat.col }}번</span>
                  <span class="font-medium">{{ seat.price.toLocaleString() }}원</span>
                </div>
              </div>
            </div>
            <div class="flex items-center justify-between border-t border-dashed border-[#ddd] pt-4"><span class="font-bold text-[#333]">총 결제금액</span><span class="text-2xl font-bold text-[#FF6B00]">{{ totalAmount.toLocaleString() }}원</span></div>
          </div>

          <div class="mb-6 space-y-2 rounded-sm bg-[#f9f9f9] p-4 text-xs text-[#666]">
            <label class="flex cursor-pointer items-center font-bold text-[#333]"><input type="checkbox" class="mr-2 accent-[#FF6B00]" :checked="agreements.all" @change="toggleAll" />전체 동의하기</label>
            <div class="space-y-1 pl-5">
              <label class="flex cursor-pointer items-center"><input type="checkbox" class="mr-2 accent-[#FF6B00]" :checked="agreements.terms" @change="agreements.terms = !agreements.terms; updateAllFlag()" />이용약관 동의 (필수)</label>
              <label class="flex cursor-pointer items-center"><input type="checkbox" class="mr-2 accent-[#FF6B00]" :checked="agreements.privacy" @change="agreements.privacy = !agreements.privacy; updateAllFlag()" />개인정보 수집 및 이용 동의 (필수)</label>
              <label class="flex cursor-pointer items-center"><input type="checkbox" class="mr-2 accent-[#FF6B00]" :checked="agreements.refund" @change="agreements.refund = !agreements.refund; updateAllFlag()" />취소/환불 규정 동의 (필수)</label>
            </div>
          </div>

          <button
            class="flex w-full items-center justify-center rounded-sm py-4 text-lg font-bold transition-all"
            :class="agreements.terms && agreements.privacy && agreements.refund ? 'bg-[#FF6B00] text-white shadow-md hover:bg-[#e56000]' : 'cursor-not-allowed bg-[#e0e0e0] text-[#999]'"
            :disabled="!agreements.terms || !agreements.privacy || !agreements.refund"
            @click="handlePayment"
          >
            <span>{{ totalAmount.toLocaleString() }}원 결제하기</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template> -->

<script setup>

console.log("PaymentScreen mounted")

import { ref, computed } from 'vue'
import { loadTossPayments } from '@tosspayments/payment-sdk'



// -----------------------------
// 환경변수
// -----------------------------
const BACKEND_BASE = import.meta.env.VITE_BACKEND_BASE_URL || 'http://localhost:8081'
const TOSS_CLIENT_KEY = import.meta.env.VITE_TOSS_CLIENT_KEY || import.meta.env.VITE_TEST_CLIENT_KEY

// -----------------------------
// 입력값 (임시 테스트용)
// -----------------------------
const bookingId = ref('')     // UUID
const userId = ref('')        // Long (create에 사용)
const seatId = ref('')        // Long (create에 사용)
const amount = ref('1000')    // BigDecimal
const orderName = ref('Ticket Payment')

// submit은 현재 백엔드가 UUID 헤더를 요구했었으니 테스트용 UUID를 따로 둠
const submitUserUuid = ref('00000000-0000-0000-0000-000000000002')

// -----------------------------
// 상태
// -----------------------------
const loading = ref(false)
const errorMsg = ref('')
const createResp = ref(null)
const submitResp = ref(null)

const canStart = computed(() => {
  return Boolean(bookingId.value?.trim())
    && String(userId.value).trim() !== ''
    && String(seatId.value).trim() !== ''
    && String(amount.value).trim() !== ''
    && Boolean(TOSS_CLIENT_KEY)
})

// -----------------------------
// 공통 fetch
// -----------------------------
async function apiFetch(path, options = {}) {
  const res = await fetch(`${BACKEND_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })

  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }

  if (!res.ok) {
    const msg = typeof data === 'object' && data?.message ? data.message : (text || `${res.status}`)
    throw new Error(`HTTP ${res.status} - ${msg}`)
  }
  return data
}

// -----------------------------
// 1) create (DTO 기준)
// -----------------------------
async function createPayment() {
  const payload = {
    bookingId: bookingId.value.trim(),
    userId: Number(userId.value),
    seatId: Number(seatId.value),
    amount: Number(amount.value),
  }

  return await apiFetch('/payments/create', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// -----------------------------
// 2) submit (컨트롤러 기준: 헤더 X-USER-ID: UUID)
// -----------------------------
async function submitPayment(paymentId) {
  const headerUuid = submitUserUuid.value.trim()

  return await apiFetch(`/payments/${paymentId}/submit`, {
    method: 'POST',
    body: JSON.stringify({}),
    headers: {
      'X-USER-ID': headerUuid,
    },
  })
}

// -----------------------------
// 3) 토스 결제창 요청 (redirect)
// -----------------------------
async function openTossCheckout(submitData) {
  const tossPayments = await loadTossPayments(TOSS_CLIENT_KEY)

  await tossPayments.requestPayment('카드', {
    amount: Number(submitData.amount),
    orderId: submitData.orderId,
    orderName: submitData.orderName || orderName.value,
    successUrl: submitData.successUrl,
    failUrl: submitData.failUrl,
  })
}

// -----------------------------
// 버튼 핸들러: create → submit → 결제창
// -----------------------------
async function handlePay() {
  errorMsg.value = ''
  createResp.value = null
  submitResp.value = null

  if (!TOSS_CLIENT_KEY) {
    errorMsg.value = 'VITE_TOSS_CLIENT_KEY 또는 VITE_TEST_CLIENT_KEY가 없습니다. frontend/.env 확인 후 dev 서버 재시작하세요.'
    return
  }

  try {
    loading.value = true

    const c = await createPayment()
    createResp.value = c

    const s = await submitPayment(c.paymentId)
    submitResp.value = s

    // submit에서 내려준 orderName을 우선 사용
    await openTossCheckout(s)
  } catch (e) {
    errorMsg.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="max-width: 720px; margin: 0 auto; padding: 16px;">
    <h2 style="margin-bottom: 12px;">Toss 결제창(redirect) 테스트 화면 (임시)</h2>

    <div style="display: grid; gap: 10px; padding: 12px; border: 1px solid #ddd; border-radius: 8px;">
      <label>
        bookingId (UUID)
        <input v-model="bookingId" placeholder="51524b5e-c3e8-4189-93f1-14df7dcd491f" style="width: 100%; padding: 8px;" />
      </label>

      <label>
        userId (Long) - create에 사용
        <input v-model="userId" placeholder="2" style="width: 100%; padding: 8px;" />
      </label>

      <label>
        seatId (Long) - create에 사용
        <input v-model="seatId" placeholder="3" style="width: 100%; padding: 8px;" />
      </label>

      <label>
        amount
        <input v-model="amount" placeholder="1000" style="width: 100%; padding: 8px;" />
      </label>

      <label>
        submitUserUuid (UUID) - submit 헤더 X-USER-ID
        <input v-model="submitUserUuid" placeholder="00000000-0000-0000-0000-000000000002" style="width: 100%; padding: 8px;" />
      </label>

      <button
        :disabled="loading || !canStart"
        @click="handlePay"
        style="padding: 10px; border-radius: 8px; border: 1px solid #333; cursor: pointer;"
      >
        {{ loading ? '진행 중...' : '결제하기 (create → submit → 토스 결제창)' }}
      </button>

      <div v-if="errorMsg" style="color: #b00020; white-space: pre-wrap;">
        {{ errorMsg }}
      </div>
    </div>

    <div style="margin-top: 16px; display: grid; gap: 12px;">
      <div v-if="createResp" style="padding: 12px; border: 1px solid #ddd; border-radius: 8px;">
        <div style="font-weight: 700; margin-bottom: 6px;">create 응답</div>
        <pre style="margin: 0; white-space: pre-wrap;">{{ JSON.stringify(createResp, null, 2) }}</pre>
      </div>

      <div v-if="submitResp" style="padding: 12px; border: 1px solid #ddd; border-radius: 8px;">
        <div style="font-weight: 700; margin-bottom: 6px;">submit 응답</div>
        <pre style="margin: 0; white-space: pre-wrap;">{{ JSON.stringify(submitResp, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>


