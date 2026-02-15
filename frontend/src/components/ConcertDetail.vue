<script setup lang="ts">
import { AlertCircle, ChevronLeft, ChevronRight, Clock, Info } from 'lucide-vue-next';
import { ref } from 'vue';

const emit = defineEmits<{
  bookingStart: [date: string, session: string];
}>();

const selectedDate = ref<string | null>(null);
const selectedSession = ref<string | null>(null);

const dates = Array.from({ length: 30 }, (_, i) => {
  const day = i + 1;
  const isWeekend = [7, 8, 14, 15, 21, 22, 28, 29].includes(day);
  const isAvailable = [12, 13, 14, 15].includes(day);
  const isSoldOut = [12].includes(day);
  return { day, isWeekend, isAvailable, isSoldOut };
});

const sessions = [
  { id: '1', time: '18:00', status: '여유', color: 'bg-green-100 text-green-700' },
  { id: '2', time: '20:00', status: '매진임박', color: 'bg-red-100 text-red-600 animate-pulse' }
];

const handleDateClick = (day: number, isAvailable: boolean) => {
  if (!isAvailable) {
    return;
  }
  selectedDate.value = `2025-06-${day}`;
  selectedSession.value = null;
};
</script>

<template>
  <div class="mx-auto max-w-[1200px] p-3 sm:p-4 md:p-8">
    <div class="flex flex-col gap-6 md:gap-8 lg:flex-row">
      <div class="space-y-6 lg:w-1/3">
        <div class="group relative aspect-[3/4] w-full overflow-hidden rounded-sm bg-gradient-to-br from-gray-800 to-gray-900 shadow-lg">
          <div class="absolute inset-0 flex items-center justify-center text-4xl font-bold text-white text-opacity-20 md:text-6xl">POSTER</div>
          <div class="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent p-4 md:p-6">
            <h2 class="mb-1 text-xl font-bold text-white md:text-2xl">IU 2025 HEREH</h2>
            <p class="font-medium text-orange-400">WORLD TOUR ENCORE</p>
          </div>
        </div>

        <div class="rounded-sm border border-[#e0e0e0] bg-white p-4">
          <h3 class="mb-4 border-b border-[#eee] pb-2 text-lg font-bold">공연 정보</h3>
          <table class="w-full text-sm">
            <tbody class="divide-y divide-[#f0f0f0]">
              <tr>
                <th class="w-24 py-2 text-left font-normal text-[#666]">공연기간</th>
                <td class="py-2 text-[#333]">2025.06.12 ~ 2025.06.15</td>
              </tr>
              <tr>
                <th class="py-2 text-left font-normal text-[#666]">공연장소</th>
                <td class="py-2 text-[#333]">서울 상암 월드컵경기장</td>
              </tr>
              <tr>
                <th class="py-2 text-left font-normal text-[#666]">관람시간</th>
                <td class="py-2 text-[#333]">150분</td>
              </tr>
              <tr>
                <th class="py-2 text-left font-normal text-[#666]">관람등급</th>
                <td class="py-2 text-[#333]">8세 이상 관람가</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="space-y-6 lg:w-2/3">
        <div class="rounded-sm border border-[#e0e0e0] bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-[#e0e0e0] bg-[#f5f5f5] px-4 py-3 md:px-6 md:py-4">
            <h2 class="text-base font-bold text-[#333] md:text-xl">날짜/회차 선택</h2>
            <div class="hidden items-center space-x-2 text-sm text-[#666] md:flex">
              <div class="flex items-center"><div class="mr-1 h-3 w-3 rounded-full bg-[#FF6B00]"></div>선택가능</div>
              <div class="flex items-center"><div class="mr-1 h-3 w-3 rounded-full bg-gray-300"></div>매진/선택불가</div>
            </div>
          </div>

          <div class="flex flex-col gap-6 p-4 md:gap-8 md:p-6 md:flex-row">
            <div class="flex-1">
              <div class="mb-4 flex items-center justify-between">
                <button class="rounded p-1 hover:bg-gray-100"><ChevronLeft :size="20" /></button>
                <span class="text-lg font-bold">2025.06</span>
                <button class="rounded p-1 hover:bg-gray-100"><ChevronRight :size="20" /></button>
              </div>
              <div class="mb-2 grid grid-cols-7 gap-1 text-center text-xs md:text-sm">
                <div class="text-red-500">일</div><div>월</div><div>화</div><div>수</div><div>목</div><div>금</div><div class="text-blue-500">토</div>
              </div>
              <div class="grid grid-cols-7 gap-1 text-center">
                <div class="p-2"></div><div class="p-2"></div><div class="p-2"></div>
                <button
                  v-for="d in dates"
                  :key="d.day"
                  :disabled="!d.isAvailable && !d.isSoldOut"
                  class="relative mx-auto flex h-9 w-9 items-center justify-center rounded-full p-2 text-sm transition-all md:h-10 md:w-10 md:text-base"
                  :class="[
                    selectedDate === `2025-06-${d.day}`
                      ? 'bg-[#FF6B00] font-bold text-white shadow-md'
                      : d.isAvailable
                        ? 'cursor-pointer border border-transparent font-semibold text-[#333] hover:border-orange-200 hover:bg-orange-50'
                        : 'cursor-default text-gray-300',
                    d.isWeekend && !selectedDate?.includes(String(d.day)) ? 'text-red-400' : ''
                  ]"
                  @click="handleDateClick(d.day, d.isAvailable)"
                >
                  <span :class="d.isSoldOut ? 'line-through' : ''">{{ d.day }}</span>
                  <span v-if="d.isSoldOut" class="absolute -bottom-1 text-[10px] font-bold text-red-500">매진</span>
                </button>
              </div>
            </div>

            <div class="flex-1 border-t border-[#f0f0f0] pt-6 md:border-l md:border-t-0 md:pl-8 md:pt-0">
              <div class="mb-6">
                <h3 class="mb-3 flex items-center font-bold text-[#333]"><Clock :size="16" class="mr-2 text-[#FF6B00]" />회차 선택</h3>
                <div v-if="selectedDate" class="space-y-2">
                  <button
                    v-for="session in sessions"
                    :key="session.id"
                    class="flex w-full items-center justify-between rounded-sm border p-3 transition-all"
                    :class="selectedSession === session.id ? 'border-[#FF6B00] bg-orange-50 ring-1 ring-[#FF6B00]' : 'border-[#ddd] bg-white hover:border-gray-400'"
                    @click="selectedSession = session.id"
                  >
                    <span class="font-bold text-[#333]">{{ session.id }}회차 {{ session.time }}</span>
                    <span class="rounded-full px-2 py-1 text-xs font-medium" :class="session.color">{{ session.status }}</span>
                  </button>
                </div>
                <div v-else class="rounded border border-dashed border-gray-200 bg-gray-50 py-8 text-center text-gray-400">날짜를 먼저 선택해주세요</div>
              </div>

              <div>
                <h3 class="mb-3 flex items-center font-bold text-[#333]"><Info :size="16" class="mr-2 text-[#FF6B00]" />좌석 등급 및 가격</h3>
                <div class="overflow-hidden rounded-sm border border-[#e0e0e0]">
                  <table class="w-full text-xs md:text-sm">
                    <thead class="bg-[#f5f5f5] text-[#666]"><tr><th class="px-3 py-2 text-left font-medium">등급</th><th class="px-3 py-2 text-right font-medium">가격</th></tr></thead>
                    <tbody class="divide-y divide-[#f0f0f0]">
                      <tr><td class="flex items-center px-3 py-2"><div class="mr-2 h-2 w-2 rounded-full bg-[#E8000B]"></div>VIP석</td><td class="px-3 py-2 text-right font-bold">165,000원</td></tr>
                      <tr><td class="flex items-center px-3 py-2"><div class="mr-2 h-2 w-2 rounded-full bg-[#FF6B00]"></div>R석</td><td class="px-3 py-2 text-right font-bold">143,000원</td></tr>
                      <tr><td class="flex items-center px-3 py-2"><div class="mr-2 h-2 w-2 rounded-full bg-[#3B82F6]"></div>S석</td><td class="px-3 py-2 text-right font-bold">110,000원</td></tr>
                      <tr><td class="flex items-center px-3 py-2"><div class="mr-2 h-2 w-2 rounded-full bg-[#22C55E]"></div>A석</td><td class="px-3 py-2 text-right font-bold">88,000원</td></tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="rounded-sm border border-[#ffe082] bg-[#fff9e6] p-4">
          <h4 class="mb-2 flex items-center font-bold text-[#d48806]"><AlertCircle :size="16" class="mr-2" />유의사항</h4>
          <ul class="list-inside list-disc space-y-1 text-xs text-[#8c6b1f]">
            <li>본 공연은 1인 4매까지 예매 가능합니다.</li>
            <li>예매 시 본인인증이 필수이며, 공연 당일 신분증을 지참하셔야 합니다.</li>
            <li>티켓의 임의적인 상거래는 불법이며, 적발 시 강제 취소될 수 있습니다.</li>
          </ul>
        </div>

        <button
          class="w-full rounded-sm py-3 text-base font-bold shadow-md transition-all md:py-4 md:text-lg"
          :class="selectedDate && selectedSession ? 'bg-[#FF6B00] text-white hover:-translate-y-0.5 hover:bg-[#e56000]' : 'cursor-not-allowed bg-[#e0e0e0] text-[#999]'"
          :disabled="!selectedDate || !selectedSession"
          @click="selectedDate && selectedSession && emit('bookingStart', selectedDate, selectedSession)"
        >
          {{ selectedDate && selectedSession ? '예매하기' : '날짜와 회차를 선택해주세요' }}
        </button>
      </div>
    </div>
  </div>
</template>
