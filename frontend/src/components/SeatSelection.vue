<script setup lang="ts">
import { AlertCircle, X, ZoomIn } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { Seat } from '../types';

const emit = defineEmits<{
  complete: [seats: Seat[]];
}>();

const timeLeft = ref(420);
const selectedSection = ref<string | null>(null);
const selectedSeats = ref<Seat[]>([]);
const showConflictModal = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  timer = setInterval(() => {
    if (timeLeft.value <= 0) {
      return;
    }
    timeLeft.value -= 1;
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
});

const sections = [
  { id: 'VIP', color: 'bg-[#E8000B]', borderColor: '#E8000B', price: 165000, label: 'VIP석' },
  { id: 'R', color: 'bg-[#FF6B00]', borderColor: '#FF6B00', price: 143000, label: 'R석' },
  { id: 'S', color: 'bg-[#3B82F6]', borderColor: '#3B82F6', price: 110000, label: 'S석' },
  { id: 'A', color: 'bg-[#22C55E]', borderColor: '#22C55E', price: 88000, label: 'A석' }
];

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
};

const progressWidth = computed(() => `${(timeLeft.value / 420) * 100}%`);
const totalAmount = computed(() => selectedSeats.value.reduce((sum, seat) => sum + seat.price, 0));

const toggleSeat = (row: number, col: number, price: number, grade: string) => {
  if (!selectedSection.value) {
    return;
  }

  const seatId = `${selectedSection.value}-${row}-${col}`;
  const exists = selectedSeats.value.find((seat) => seat.id === seatId);

  if (exists) {
    selectedSeats.value = selectedSeats.value.filter((seat) => seat.id !== seatId);
    return;
  }

  if (selectedSeats.value.length >= 4) {
    alert('최대 4매까지만 선택 가능합니다.');
    return;
  }

  if (Math.random() > 0.9) {
    showConflictModal.value = true;
    return;
  }

  selectedSeats.value = [
    ...selectedSeats.value,
    {
      id: seatId,
      section: selectedSection.value,
      row,
      col,
      price,
      grade
    }
  ];
};
</script>

<template>
  <div class="flex min-h-[calc(100vh-140px)] flex-col">
    <div class="sticky top-0 z-20 flex items-center justify-between bg-[#333] px-4 py-2 text-white">
      <div class="flex items-center space-x-2">
        <span class="animate-pulse font-bold text-[#FF6B00]">●</span>
        <span class="text-sm">남은 시간</span>
        <span class="font-mono text-xl font-bold text-[#FF6B00]">{{ formatTime(timeLeft) }}</span>
      </div>
      <div class="hidden text-xs text-gray-400 md:block">제한시간 내 결제를 완료해주세요.</div>
    </div>
    <div class="h-1 w-full bg-gray-800"><div class="h-full bg-gradient-to-r from-[#FF6B00] to-[#E8000B] transition-[width] duration-1000" :style="{ width: progressWidth }"></div></div>

    <div class="flex flex-1 flex-col overflow-hidden lg:flex-row">
      <div class="relative flex flex-1 items-center justify-center overflow-auto bg-[#f0f0f0] p-3 md:p-8">
        <div v-if="!selectedSection" class="relative w-full max-w-[600px] rounded-sm bg-white p-4 shadow-xl md:p-8">
          <div class="mb-8 flex h-14 w-full items-center justify-center rounded-sm bg-[#333] font-bold text-white md:mb-12 md:h-16">STAGE</div>

          <div class="grid grid-cols-2 gap-3 md:h-[400px] md:gap-4">
            <button
              v-for="section in sections"
              :key="section.id"
              class="group flex flex-col items-center justify-center border-2 border-dashed bg-opacity-10 transition-all hover:scale-[1.02] hover:bg-opacity-20"
              :class="section.color"
              :style="{ borderColor: section.borderColor }"
              @click="selectedSection = section.id"
            >
              <span class="mb-1 text-lg font-bold md:mb-2 md:text-2xl">{{ section.id }}구역</span>
              <span class="rounded bg-white px-2 py-1 text-xs font-medium shadow-sm md:text-sm">{{ section.label }} 잔여 42석</span>
              <div class="mt-2 hidden items-center rounded-full bg-white px-2 py-1 text-xs font-bold opacity-0 shadow-md transition-opacity group-hover:opacity-100 md:mt-4 md:flex">
                <ZoomIn :size="12" class="mr-1" /> 좌석 선택하기
              </div>
            </button>
          </div>
        </div>

        <div v-else class="w-full max-w-[760px] rounded-sm bg-white p-4 shadow-xl md:p-8">
          <div class="mb-6 flex items-center justify-between border-b pb-4">
            <h3 class="text-base font-bold md:text-xl">{{ selectedSection }}구역 좌석배치도</h3>
            <button class="text-xs text-[#666] underline hover:text-[#333] md:text-sm" @click="selectedSection = null">전체 구역도로 돌아가기</button>
          </div>

          <div class="mb-8 grid grid-cols-10 gap-1 sm:gap-2">
            <button
              v-for="i in 100"
              :key="i"
              class="flex h-7 w-7 items-center justify-center rounded-[2px] border text-[10px] transition-colors sm:h-8 sm:w-8"
              :class="(() => {
                const idx = i - 1;
                const row = Math.floor(idx / 10) + 1;
                const col = (idx % 10) + 1;
                const isOccupied = idx % 5 === 0 || idx % 7 === 0;
                const seatId = `${selectedSection}-${row}-${col}`;
                const isSelected = selectedSeats.some((seat) => seat.id === seatId);
                if (isOccupied) return 'cursor-not-allowed border-gray-300 bg-gray-200 text-gray-400';
                if (isSelected) return 'border-[#FF6B00] bg-[#FF6B00] font-bold text-white';
                return 'border-gray-300 bg-white text-[#666] hover:bg-orange-50';
              })()"
              :disabled="(() => { const idx = i - 1; return idx % 5 === 0 || idx % 7 === 0; })()"
              @click="(() => {
                const idx = i - 1;
                const row = Math.floor(idx / 10) + 1;
                const col = (idx % 10) + 1;
                const sectionData = sections.find((section) => section.id === selectedSection);
                toggleSeat(row, col, sectionData?.price || 0, sectionData?.label || '');
              })()"
            >
              {{ ((i - 1) % 10) + 1 }}
            </button>
          </div>

          <div class="flex flex-wrap justify-center gap-4 text-xs text-[#666] sm:space-x-6">
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded-[2px] border border-[#FF6B00] bg-[#FF6B00]"></div>선택</div>
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded-[2px] border border-gray-300 bg-white"></div>선택가능</div>
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded-[2px] border border-gray-300 bg-gray-200"></div>선택불가</div>
          </div>
        </div>
      </div>

      <div class="z-10 flex w-full flex-col border-t border-[#e0e0e0] bg-white shadow-[-4px_0_15px_rgba(0,0,0,0.05)] lg:w-[320px] lg:border-l lg:border-t-0">
        <div class="flex items-center justify-between bg-[#333] p-4 font-bold text-white"><span>선택좌석</span><span class="text-[#FF6B00]">{{ selectedSeats.length }}석</span></div>

        <div class="flex-1 space-y-3 overflow-y-auto p-4">
          <div v-if="selectedSeats.length === 0" class="py-10 text-center text-sm text-gray-400">좌석을 선택해주세요.</div>
          <div
            v-for="seat in selectedSeats"
            :key="seat.id"
            class="group relative rounded-sm border border-[#e0e0e0] bg-white p-3 shadow-sm"
          >
            <button class="absolute right-2 top-2 text-gray-400 hover:text-red-500" @click="toggleSeat(seat.row, seat.col, seat.price, seat.grade)">
              <X :size="16" />
            </button>
            <div class="mb-1 flex items-center">
              <span
                class="mr-2 h-2 w-2 rounded-full"
                :class="seat.section === 'VIP' ? 'bg-[#E8000B]' : seat.section === 'R' ? 'bg-[#FF6B00]' : seat.section === 'S' ? 'bg-[#3B82F6]' : 'bg-[#22C55E]'"
              ></span>
              <span class="text-sm font-bold text-[#333]">{{ seat.grade }}</span>
            </div>
            <div class="mb-1 text-xs text-[#666]">{{ seat.section }}구역 {{ seat.row }}열 {{ seat.col }}번</div>
            <div class="text-right font-bold text-[#333]">{{ seat.price.toLocaleString() }}원</div>
          </div>
        </div>

        <div class="border-t border-[#e0e0e0] bg-[#f9f9f9] p-4">
          <div class="mb-4 flex items-center justify-between"><span class="text-sm font-bold text-[#333]">총 결제금액</span><span class="text-xl font-bold text-[#FF6B00]">{{ totalAmount.toLocaleString() }}원</span></div>
          <button
            class="w-full rounded-sm py-4 text-lg font-bold transition-all"
            :class="selectedSeats.length > 0 ? 'bg-[#FF6B00] text-white shadow-md hover:bg-[#e56000]' : 'cursor-not-allowed bg-[#e0e0e0] text-[#999]'"
            :disabled="selectedSeats.length === 0"
            @click="emit('complete', selectedSeats)"
          >
            좌석선택 완료
          </button>
        </div>
      </div>
    </div>

    <div v-if="showConflictModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="w-full max-w-sm rounded-sm bg-white p-6 text-center shadow-2xl">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100"><AlertCircle class="text-[#E8000B]" :size="24" /></div>
        <h3 class="mb-2 text-lg font-bold">이미 선택된 좌석입니다.</h3>
        <p class="mb-6 text-sm text-[#666]">
          선택하신 좌석이 다른 사용자에 의해
          <br />
          먼저 선점되었습니다.
          <br />
          다른 좌석을 선택해주세요.
        </p>
        <button class="w-full rounded-sm bg-[#333] py-3 font-bold text-white hover:bg-black" @click="showConflictModal = false">확인</button>
      </div>
    </div>
  </div>
</template>
