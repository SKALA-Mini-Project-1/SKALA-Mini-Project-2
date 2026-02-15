<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next';
import { computed, reactive, ref } from 'vue';
import type { BookingData } from '../types';

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

const handlePayment = () => {
  isProcessing.value = true;
  setTimeout(() => {
    isProcessing.value = false;
    emit('paymentComplete');
  }, 2000);
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
            <div><div class="mb-1 text-xs text-[#999]">공연명</div><div class="font-bold text-[#333]">IU 2025 HEREH WORLD TOUR</div></div>
            <div><div class="mb-1 text-xs text-[#999]">일시</div><div class="text-sm text-[#333]">{{ bookingData.date }} {{ bookingData.session }}회차</div></div>
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
</template>
