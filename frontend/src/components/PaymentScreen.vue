<script setup lang="ts">
import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk';
import { Loader2 } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { cancelPayment, createPayment, sendPaymentExitSignal, submitPayment } from '../data/payments';
import { apiRequest, ApiError } from '../services/api';
import { getAuthUser, getToken } from '../services/auth';
import { clearActivePaymentSession, getActivePaymentSession, setActivePaymentSession } from '../services/paymentSession';
import { leaveSeatScreen } from '../services/seat';
import type { BookingData } from '../types';

const props = defineProps<{
  bookingData: BookingData;
}>();

const emit = defineEmits<{
  navigate: [path: string];
}>();

const isProcessing = ref(false);
const paymentMethod = ref('card');
const skipLeaveCleanup = ref(false);
const hasSentLeave = ref(false);
const hasHandledPaymentExit = ref(false);
const agreements = reactive({
  all: false,
  terms: false,
  privacy: false,
  refund: false
});
const manualBookingId = ref('');
const payerName = ref('');
const payerPhone = ref('');
const payerEmail = ref('');
const activePaymentId = ref<string | null>(null);
watch(
  () => props.bookingData.bookingId,
  (bookingId) => {
    manualBookingId.value = bookingId ?? '';
  },
  { immediate: true }
);

interface BookingLookupResponse {
  userId?: number;
  user_id?: number;
}

interface MyInfoResponse {
  userId: number;
  email: string;
  name: string;
  phone: string;
}

const extractBookingUserId = (booking: BookingLookupResponse): number | null => {
  if (Number.isFinite(booking.userId)) {
    return Number(booking.userId);
  }
  if (Number.isFinite(booking.user_id)) {
    return Number(booking.user_id);
  }
  return null;
};

const loadPayerInfo = async () => {
  const bookingId = (props.bookingData.bookingId || manualBookingId.value || '').trim();
  const token = getToken();
  const authUser = getAuthUser();

  if (authUser) {
    payerName.value = authUser.name ?? '';
    payerEmail.value = authUser.email ?? '';
  }

  if (!token) {
    return;
  }

  try {
    const me = await apiRequest<MyInfoResponse>('/api/users/me', {
      method: 'GET',
      token
    });

    if (bookingId) {
      try {
        const booking = await apiRequest<BookingLookupResponse>(`/api/bookings/${bookingId}`, {
          method: 'GET',
          token
        });
        const bookingUserId = extractBookingUserId(booking);
        if (bookingUserId && bookingUserId !== me.userId) {
          console.warn('[PaymentScreen] booking user mismatch', {
            bookingId,
            bookingUserId,
            loginUserId: me.userId
          });
        }
      } catch (bookingError) {
        console.warn('[PaymentScreen] booking lookup skipped', bookingError);
      }
    }

    payerName.value = me.name ?? '';
    payerPhone.value = me.phone ?? '';
    payerEmail.value = me.email ?? '';
  } catch (error) {
    if (error instanceof ApiError) {
      console.error('[PaymentScreen] payer info load failed', {
        status: error.status,
        message: error.message
      });
      return;
    }
    console.error('[PaymentScreen] payer info load failed', error);
  }
};

watch(
  () => props.bookingData.bookingId,
  () => {
    void loadPayerInfo();
  },
  { immediate: true }
);
const isUuid = (value: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);

const totalAmount = computed(() => props.bookingData.seats.reduce((sum, seat) => sum + seat.price, 0));

const leaveSeatOnExit = async () => {
  if (hasSentLeave.value) {
    return;
  }
  const concertId = Number(props.bookingData.concertId);
  const scheduleId = Number(props.bookingData.scheduleId);
  if (!Number.isFinite(concertId) || !Number.isFinite(scheduleId)) {
    return;
  }

  hasSentLeave.value = true;
  try {
    await leaveSeatScreen(concertId, scheduleId);
  } catch (error) {
    console.error('결제 화면 이탈 처리 실패', error);
  }
};

const resolveActivePaymentId = () => activePaymentId.value ?? getActivePaymentSession()?.paymentId ?? null;

const buildExitPayload = (reasonCode: string, source: string, clientRoute = window.location.pathname) => ({
  reasonCode,
  source,
  clientRoute
});

const triggerKeepaliveExitRequests = (reasonCode: string, source: string) => {
  const paymentId = resolveActivePaymentId();
  const token = getToken();
  if (!paymentId || !token) {
    return;
  }

  const payload = buildExitPayload(reasonCode, source);
  void sendPaymentExitSignal(paymentId, token, payload, true).catch(() => undefined);
  void cancelPayment(paymentId, token, payload, true).catch(() => undefined);
};

const handlePageHide = () => {
  void handlePaymentExit('PAGEHIDE', 'BROWSER_PAGEHIDE', true);
};

const handlePaymentExit = async (reasonCode: string, source: string, keepalive = false) => {
  if (skipLeaveCleanup.value || hasHandledPaymentExit.value) {
    return;
  }

  hasHandledPaymentExit.value = true;

  const paymentId = resolveActivePaymentId();
  const token = getToken();
  const payload = buildExitPayload(reasonCode, source);

  if (paymentId && token) {
    if (keepalive) {
      triggerKeepaliveExitRequests(reasonCode, source);
    } else {
      try {
        await sendPaymentExitSignal(paymentId, token, payload);
      } catch (error) {
        console.warn('[PaymentScreen] payment exit signal failed', error);
      }

      try {
        await cancelPayment(paymentId, token, payload);
        clearActivePaymentSession();
      } catch (error) {
        console.warn('[PaymentScreen] payment cancel best-effort failed', error);
      }
    }
  }

  await leaveSeatOnExit();
};

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

const handlePayment = async () => {
  if (isProcessing.value) {
    return;
  }

  if (!agreements.terms || !agreements.privacy || !agreements.refund) {
    return;
  }

  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;
  if (!clientKey) {
    alert('결제 설정이 누락되었습니다. 관리자에게 문의해주세요.');
    return;
  }

  try {
    isProcessing.value = true;
    skipLeaveCleanup.value = true;

    const tossPayments = await loadTossPayments(clientKey);
    const authUser = getAuthUser();
    const token = getToken();
    if (!authUser?.userId || !token) {
      alert('로그인이 필요합니다. 다시 로그인 후 시도해주세요.');
      return;
    }

    const manualId = manualBookingId.value.trim();
    const bookingId = manualId || props.bookingData.bookingId || null;
    if (!bookingId) {
      alert('예매 정보가 없습니다. 다시 좌석 선택 후 시도해주세요.');
      return;
    }
    if (!isUuid(bookingId)) {
      alert('bookingId는 UUID 형식이어야 합니다.');
      return;
    }

    const currentSession = getActivePaymentSession();
    const currentPaymentId =
      currentSession?.bookingId === bookingId
        ? (activePaymentId.value ?? currentSession.paymentId)
        : null;
    if (currentPaymentId) {
      try {
        await cancelPayment(currentPaymentId, token, buildExitPayload('PAYMENT_METHOD_CHANGED', 'PAYMENT_RETRY_BEFORE_SUBMIT'));
      } catch (error) {
        console.warn('[PaymentScreen] existing payment cancel failed before retry', error);
      } finally {
        clearActivePaymentSession();
        activePaymentId.value = null;
      }
    }

    const created = await createPayment({ bookingId, userId: authUser.userId }, token);
    activePaymentId.value = created.paymentId;
    setActivePaymentSession({
      paymentId: created.paymentId,
      bookingId
    });
    const submitted = await submitPayment(created.paymentId, token);

    const amount = submitted.amount;
    const orderId = submitted.orderId;
    const orderName = submitted.orderName || (props.bookingData.concertTitle ? `${props.bookingData.concertTitle} 티켓` : '공연 티켓');
    const customerName = authUser.name ?? '고객';
    // Always use current frontend origin so auth/session state is preserved after PG redirect.
    const successUrl = `${window.location.origin}/payments/success`;
    const failUrl = `${window.location.origin}/payments/fail`;

    const customerKey = submitted.customerKey ?? `USER_${authUser.userId}` ?? ANONYMOUS;
    const payment = tossPayments.payment({ customerKey });
    skipLeaveCleanup.value = true;

    if (paymentMethod.value === 'vbank') {
      await payment.requestPayment({
        method: 'VIRTUAL_ACCOUNT',
        amount: { currency: 'KRW', value: amount },
        orderId,
        orderName,
        customerName,
        successUrl,
        failUrl
      });
      return;
    }

    const isTossPay = paymentMethod.value === 'toss' || paymentMethod.value === 'simple';

    await payment.requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: amount },
      orderId,
      orderName,
      customerName,
      successUrl,
      failUrl,
      card: isTossPay
        ? {
            flowMode: 'DIRECT',
            easyPay: 'TOSSPAY'
          }
        : undefined
    });
  } catch (error) {
    skipLeaveCleanup.value = false;
    console.error('Toss Payments error:', error);
    const message = error instanceof Error ? error.message : '결제창을 여는 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
    alert(message);
  } finally {
    isProcessing.value = false;
  }
};

const handleCancelPayment = async () => {
  if (isProcessing.value) {
    return;
  }

  isProcessing.value = true;
  try {
    await handlePaymentExit('USER_CANCEL_BUTTON', 'PAYMENT_SCREEN_CANCEL');
    clearActivePaymentSession();
    activePaymentId.value = null;
    emit('navigate', '/concert/seat');
  } finally {
    isProcessing.value = false;
  }
};

onMounted(() => {
  skipLeaveCleanup.value = false;
  hasSentLeave.value = false;
  hasHandledPaymentExit.value = false;
  activePaymentId.value = getActivePaymentSession()?.paymentId ?? null;
  window.addEventListener('pagehide', handlePageHide);
  void loadPayerInfo();
});

onBeforeUnmount(() => {
  window.removeEventListener('pagehide', handlePageHide);
  void handlePaymentExit('COMPONENT_UNMOUNT', 'PAYMENT_SCREEN_UNMOUNT');
});
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
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row">
              <div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">예약 ID</div>
              <div class="flex-1 p-3">
                <input
                  v-model="manualBookingId"
                  type="text"
                  class="w-full border border-[#ddd] bg-[#f5f6f8] px-3 py-2 text-sm text-[#333] sm:max-w-xs"
                  readonly
                />
              </div>
            </div>
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row">
              <div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">이름</div>
              <div class="flex-1 p-3">
                <input
                  v-model="payerName"
                  type="text"
                  class="w-full border border-[#ddd] bg-[#f5f6f8] px-3 py-2 text-sm text-[#333] sm:max-w-xs"
                  readonly
                />
              </div>
            </div>
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row">
              <div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">연락처</div>
              <div class="flex-1 p-3">
                <input
                  v-model="payerPhone"
                  type="text"
                  class="w-full border border-[#ddd] bg-[#f5f6f8] px-3 py-2 text-sm text-[#333] sm:max-w-xs"
                  readonly
                />
              </div>
            </div>
            <div class="flex flex-col border-b border-[#e0e0e0] sm:flex-row">
              <div class="flex w-full items-center bg-[#f9f9f9] p-3 text-sm font-medium text-[#666] sm:w-32">이메일</div>
              <div class="flex-1 p-3">
                <input
                  v-model="payerEmail"
                  type="email"
                  class="w-full border border-[#ddd] bg-[#f5f6f8] px-3 py-2 text-sm text-[#333] sm:max-w-xs"
                  readonly
                />
              </div>
            </div>
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
          <button
            type="button"
            class="mt-3 w-full rounded-sm border border-[#cfd8e3] py-3 text-sm font-bold text-[#35516f] transition-colors hover:bg-[#f5f8fc]"
            :disabled="isProcessing"
            @click="handleCancelPayment"
          >
            결제 취소
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
