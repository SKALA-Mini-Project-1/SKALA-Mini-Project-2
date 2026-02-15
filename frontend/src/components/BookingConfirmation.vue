<script setup lang="ts">
import { Check, Home, QrCode, Smartphone } from 'lucide-vue-next';
import { computed } from 'vue';
import type { BookingData } from '../types';

const props = defineProps<{
  bookingData: BookingData;
}>();

const emit = defineEmits<{
  navigate: [path: string];
}>();

const totalAmount = computed(() => props.bookingData.seats.reduce((sum, seat) => sum + seat.price, 0));
</script>

<template>
  <div class="mx-auto max-w-[800px] p-3 sm:p-4 md:p-8">
    <div class="mb-8 text-center md:mb-12">
      <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[#FF6B00] shadow-lg md:mb-6 md:h-20 md:w-20">
        <Check :size="40" class="text-white" />
      </div>
      <h2 class="mb-2 text-2xl font-bold text-[#333] md:text-3xl">예매가 완료되었습니다!</h2>
      <p class="text-sm text-[#666] md:text-base">
        예매 내역은 마이페이지에서 확인하실 수 있습니다.
        <br />
        공연 당일 현장에서 모바일 티켓을 제시해주세요.
      </p>
    </div>

    <div class="mb-8 overflow-hidden rounded-sm border border-[#e0e0e0] bg-white shadow-sm">
      <div class="flex flex-col items-start gap-1 border-b border-[#e0e0e0] bg-[#f8f8f8] px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6 sm:py-4">
        <span class="font-bold text-[#333]">예매정보</span>
        <span class="text-xs text-[#666] sm:text-sm">예매번호: <span class="ml-1 text-base font-bold text-[#FF6B00] sm:text-lg">T2025-0612-15847</span></span>
      </div>

      <div class="p-4 sm:p-6">
        <table class="w-full text-xs sm:text-sm">
          <tbody class="divide-y divide-[#f0f0f0]">
            <tr><th class="w-32 py-3 text-left font-normal text-[#666]">공연명</th><td class="py-3 font-bold text-[#333]">IU 2025 HEREH WORLD TOUR ENCORE</td></tr>
            <tr><th class="py-3 text-left font-normal text-[#666]">일시</th><td class="py-3 text-[#333]">{{ bookingData.date }} {{ bookingData.session }}회차</td></tr>
            <tr><th class="py-3 text-left font-normal text-[#666]">장소</th><td class="py-3 text-[#333]">서울 상암 월드컵경기장</td></tr>
            <tr>
              <th class="py-3 text-left font-normal text-[#666]">좌석</th>
              <td class="py-3 text-[#333]"><div v-for="(seat, index) in bookingData.seats" :key="`${seat.id}-${index}`">{{ seat.grade }} {{ seat.section }}구역 {{ seat.row }}열 {{ seat.col }}번</div></td>
            </tr>
            <tr><th class="py-3 text-left font-normal text-[#666]">결제금액</th><td class="py-3 text-lg font-bold text-[#FF6B00]">{{ totalAmount.toLocaleString() }}원</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mb-8 flex items-start rounded-sm border border-[#ffe082] bg-[#fff9e6] p-4 text-xs text-[#8c6b1f] sm:text-sm">
      <Smartphone :size="20" class="mr-3 mt-0.5 shrink-0" />
      <div>
        <p class="mb-1 font-bold">모바일 티켓 안내</p>
        <p>
          본 공연은 모바일 티켓으로만 입장이 가능합니다.
          <br />
          공연 당일 앱 내 마이페이지에서 QR코드를 확인해주세요.
        </p>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 sm:gap-4">
      <button
        class="flex items-center justify-center space-x-2 rounded-sm border border-[#FF6B00] py-3 font-bold text-[#FF6B00] transition-colors hover:bg-orange-50 md:py-4"
        @click="emit('navigate', '/mypage')"
      >
        <QrCode :size="20" />
        <span>모바일 티켓 확인</span>
      </button>
      <button
        class="flex items-center justify-center space-x-2 rounded-sm bg-[#333] py-3 font-bold text-white transition-colors hover:bg-black md:py-4"
        @click="emit('navigate', '/concert/detail')"
      >
        <Home :size="20" />
        <span>메인으로 돌아가기</span>
      </button>
    </div>
  </div>
</template>
