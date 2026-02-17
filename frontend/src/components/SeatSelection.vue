<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError } from '../services/api';
import { getSeatMap, getSeatMapBySchedule, holdSeat, leaveSeatScreen, type SeatMapItem } from '../services/seat';
import type { Seat } from '../types';

const emit = defineEmits<{
  complete: [seats: Seat[]];
}>();

type SectionMeta = {
  id: string;
  x: number;
  y: number;
  angle: number;
  floor: 'FLOOR' | '1F' | '2F';
  grade: string;
  price: number;
  tone: 'violet' | 'emerald' | 'sky' | 'amber' | 'slate';
  totalSeats: number;
  rows: number;
  cols: number;
};
const props = defineProps<{
  concertId: string | null;
  scheduleId: string | null;
}>();

const DEFAULT_SEAT_ACCESS_TTL_SECONDS = 300;
const timeLeft = ref(DEFAULT_SEAT_ACCESS_TTL_SECONDS);
const initialTimeLeft = ref(DEFAULT_SEAT_ACCESS_TTL_SECONDS);
const selectedSection = ref<SectionMeta | null>(null);
const selectedSeats = ref<Seat[]>([]);
const showConflictModal = ref(false);
const showExpiryWarningModal = ref(false);
const hasShownExpiryWarning = ref(false);
const holdingSeatMap = ref<Record<string, boolean>>({});
const seatStatusMap = ref<Record<string, string>>({});
const shouldSkipLeaveOnUnmount = ref(false);
const hasSentLeave = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;
const concertId = computed(() => Number(props.concertId ?? 1));
const scheduleId = computed(() => (props.scheduleId ? Number(props.scheduleId) : null));

const leaveSeatOnExit = async () => {
  if (shouldSkipLeaveOnUnmount.value || hasSentLeave.value) {
    return;
  }
  if (!concertId.value || !scheduleId.value) {
    return;
  }

  hasSentLeave.value = true;
  try {
    await leaveSeatScreen(concertId.value, scheduleId.value);
  } catch (error) {
    console.error('좌석 이탈 처리 실패', error);
  }
};

const redirectToMain = () => {
  void leaveSeatOnExit();
  window.location.href = 'http://localhost:5173/main';
};

const centerX = 50;
const centerY = 28;

const makeArcSections = (
  ids: string[],
  floor: SectionMeta['floor'],
  tone: SectionMeta['tone'],
  grade: string,
  price: number,
  radius: number,
  startDeg: number,
  endDeg: number,
  seatCounts: number[],
  rows: number[],
  cols: number[]
): SectionMeta[] => {
  const count = ids.length;
  const step = count > 1 ? (endDeg - startDeg) / (count - 1) : 0;

  return ids.map((id, index) => {
    const deg = startDeg + step * index;
    const rad = (deg * Math.PI) / 180;
    const x = centerX + radius * Math.cos(rad);
    const y = centerY + radius * Math.sin(rad);
    const angle = (Math.atan2(centerY - y, centerX - x) * 180) / Math.PI + 90;

    return {
      id,
      x: Number(x.toFixed(2)),
      y: Number(y.toFixed(2)),
      angle: Number(angle.toFixed(2)),
      floor,
      grade,
      price,
      tone,
      totalSeats: seatCounts[index],
      rows: rows[index],
      cols: cols[index]
    };
  });
};

const floorSections: SectionMeta[] = [
  { id: 'A', x: 38, y: 21.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 },
  { id: 'B', x: 50, y: 21.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 },
  { id: 'C', x: 62, y: 21.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 },
  { id: 'D', x: 38, y: 34.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 },
  { id: 'E', x: 50, y: 34.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 },
  { id: 'F', x: 62, y: 34.5, angle: 0, floor: 'FLOOR', grade: 'VIP', price: 165000, tone: 'amber', totalSeats: 700, rows: 25, cols: 28 }
];

const firstFloorSections = [
  ...makeArcSections(
    ['G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'],
    '1F',
    'violet',
    'R',
    143000,
    28,
    178,
    2,
    [600, 600, 600, 600, 600, 600, 600, 600, 600],
    [24, 24, 24, 24, 24, 24, 24, 24, 24],
    [25, 25, 25, 25, 25, 25, 25, 25, 25]
  )
];

const secondFloorSections = [
  ...makeArcSections(
    ['P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'],
    '2F',
    'sky',
    'S',
    110000,
    44,
    163,
    17,
    [400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400],
    [20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20],
    [20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20]
  )
];

const sections: SectionMeta[] = [...floorSections, ...firstFloorSections, ...secondFloorSections];

const toneClassMap: Record<SectionMeta['tone'], string> = {
  violet: 'border-violet-300 bg-violet-100 text-violet-900 hover:bg-violet-200',
  emerald: 'border-emerald-300 bg-emerald-100 text-emerald-900 hover:bg-emerald-200',
  sky: 'border-sky-300 bg-sky-100 text-sky-900 hover:bg-sky-200',
  amber: 'border-amber-300 bg-amber-100 text-amber-900 hover:bg-amber-200',
  slate: 'border-slate-300 bg-slate-200 text-slate-900 hover:bg-slate-300'
};

onMounted(() => {
  shouldSkipLeaveOnUnmount.value = false;
  hasSentLeave.value = false;
  timer = setInterval(() => {
    if (timeLeft.value <= 0) {
      if (timer) {
        clearInterval(timer);
        timer = null;
      }
      alert("좌석 선택 시간이 만료되어 메인 화면으로 이동합니다.");
      redirectToMain();
      return;
    }

    if (timeLeft.value <= 30 && !hasShownExpiryWarning.value) {
      hasShownExpiryWarning.value = true;
      showExpiryWarningModal.value = true;
    }

    timeLeft.value -= 1;
  }, 1000);

  void loadSeatMap();
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
  void leaveSeatOnExit();
});

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
};

const buildSeatKey = (section: string, row: number, col: number) => `${section}-${row}-${col}`;

const loadSeatMap = async () => {
  try {
    const response = scheduleId.value
      ? await getSeatMapBySchedule(scheduleId.value)
      : await getSeatMap(concertId.value);
    const nextStatusMap: Record<string, string> = {};

    response.seats.forEach((seat: SeatMapItem) => {
      const key = buildSeatKey(seat.section, seat.rowNumber, seat.seatNumber);
      nextStatusMap[key] = seat.status;
    });

    seatStatusMap.value = nextStatusMap;

    const serverTtl = Math.floor(Number(response.seatAccessTtlSeconds ?? 0));
    if (Number.isFinite(serverTtl) && serverTtl > 0) {
      initialTimeLeft.value = serverTtl;
      timeLeft.value = serverTtl;
      hasShownExpiryWarning.value = false;
      showExpiryWarningModal.value = false;
    }
  } catch (error) {
    console.error('좌석 상태 조회 실패', error);
  }
};

const progressWidth = computed(() => {
  const base = Math.max(1, initialTimeLeft.value);
  return `${(timeLeft.value / base) * 100}%`;
});
const totalAmount = computed(() => selectedSeats.value.reduce((sum, seat) => sum + seat.price, 0));

const currentSectionSeats = computed(() => {
  if (!selectedSection.value) {
    return [] as Array<{ row: number; col: number; id: string; isOccupied: boolean; isSelected: boolean }>;
  }

  const { id, rows, cols } = selectedSection.value;
  const seats: Array<{ row: number; col: number; id: string; isOccupied: boolean; isSelected: boolean }> = [];

  for (let row = 1; row <= rows; row += 1) {
    for (let col = 1; col <= cols; col += 1) {
      const seatId = buildSeatKey(id, row, col);
      const status = seatStatusMap.value[seatId];
      const isOccupied = status === 'RESERVED';
      const isSelected = selectedSeats.value.some((seat) => seat.id === seatId);
      seats.push({ row, col, id: seatId, isOccupied, isSelected });
    }
  }

  return seats;
});

const seatStateMap = computed(() => {
  const map = new Map<string, { isOccupied: boolean; isSelected: boolean }>();

  currentSectionSeats.value.forEach((seat) => {
    map.set(seat.id, { isOccupied: seat.isOccupied, isSelected: seat.isSelected });
  });

  return map;
});

const getSeatState = (row: number, col: number) => {
  if (!selectedSection.value) {
    return { isOccupied: false, isSelected: false };
  }

  return seatStateMap.value.get(`${selectedSection.value.id}-${row}-${col}`) ?? { isOccupied: false, isSelected: false };
};

const setHolding = (seatId: string, isHolding: boolean) => {
  holdingSeatMap.value = {
    ...holdingSeatMap.value,
    [seatId]: isHolding
  };
};

const isHoldingSeat = (seatId: string) => Boolean(holdingSeatMap.value[seatId]);

const removeSelectedSeatById = (seatId: string) => {
  selectedSeats.value = selectedSeats.value.filter((seat) => seat.id !== seatId);
};

const addSelectedSeat = (seat: Seat) => {
  if (selectedSeats.value.some((item) => item.id === seat.id)) {
    return;
  }
  selectedSeats.value = [...selectedSeats.value, seat];
};

const goToSection = (section: SectionMeta) => {
  selectedSection.value = section;
};

const resetSection = () => {
  selectedSection.value = null;
};

const toggleSeat = async (row: number, col: number) => {
  if (!selectedSection.value) {
    return;
  }

  const seatId = buildSeatKey(selectedSection.value.id, row, col);
  const isOccupied = seatStatusMap.value[seatId] === 'RESERVED';

  if (isOccupied) {
    return;
  }

  if (isHoldingSeat(seatId)) {
    return;
  }

  const exists = selectedSeats.value.some((seat) => seat.id === seatId);

  if (!exists && selectedSeats.value.length >= 4) {
    alert('최대 4매까지만 선택 가능합니다.');
    return;
  }

  setHolding(seatId, true);

  try {
    if (!scheduleId.value) {
      alert('회차 정보가 없어 좌석 선점을 진행할 수 없습니다.');
      return;
    }

    const response = await holdSeat(scheduleId.value, selectedSection.value.id, row, col);
    const action = response?.action;

    if (action === 'released') {
      removeSelectedSeatById(seatId);
      return;
    }

    if (action === 'held') {
      addSelectedSeat({
        id: seatId,
        section: selectedSection.value.id,
        row,
        col,
        price: selectedSection.value.price,
        grade: selectedSection.value.grade
      });
      return;
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      showConflictModal.value = true;
      return;
    }

    alert(error instanceof Error ? error.message : '좌석 선점 요청 중 오류가 발생했습니다.');
    return;
  } finally {
    setHolding(seatId, false);
  }

  if (exists) {
    removeSelectedSeatById(seatId);
  } else {
    addSelectedSeat({
      id: seatId,
      section: selectedSection.value.id,
      row,
      col,
      price: selectedSection.value.price,
      grade: selectedSection.value.grade
    });
  }
};

const removeSeat = async (seat: Seat) => {
  if (!scheduleId.value || !seat.section) {
    removeSelectedSeatById(seat.id);
    return;
  }

  if (isHoldingSeat(seat.id)) {
    return;
  }

  setHolding(seat.id, true);
  try {
    await holdSeat(scheduleId.value, seat.section, seat.row, seat.col);
  } catch (error) {
    alert(error instanceof Error ? error.message : '좌석 해제 요청 중 오류가 발생했습니다.');
  } finally {
    removeSelectedSeatById(seat.id);
    setHolding(seat.id, false);
  }
};

const gradeLegend = computed(() => {
  const map = new Map<string, { grade: string; price: number; tone: SectionMeta['tone'] }>();

  sections.forEach((section) => {
    if (!map.has(section.grade)) {
      map.set(section.grade, {
        grade: section.grade,
        price: section.price,
        tone: section.tone
      });
    }
  });

  return Array.from(map.values());
});

const totalVenueSeats = computed(() => sections.reduce((sum, section) => sum + section.totalSeats, 0));

const completeSeatSelection = () => {
  shouldSkipLeaveOnUnmount.value = true;
  emit('complete', selectedSeats.value);
};
</script>

<template>
  <div class="flex min-h-[calc(100vh-140px)] flex-col">
    <div class="sticky top-0 z-20 flex items-center justify-between bg-[#1f2937] px-4 py-2 text-white">
      <div class="flex items-center space-x-2">
        <span class="animate-pulse font-bold text-[#f59e0b]">●</span>
        <span class="text-sm">남은 시간</span>
        <span class="font-mono text-xl font-bold text-[#f59e0b]">{{ formatTime(timeLeft) }}</span>
      </div>
      <div class="hidden text-xs text-slate-300 md:block">제한시간 내 결제를 완료해주세요.</div>
      <div class="hidden text-xs text-gray-400 md:block">
        콘서트 {{ props.concertId || '-' }} · 회차 {{ props.scheduleId || '-' }}
      </div>
    </div>
    <div class="h-1 w-full bg-slate-800"><div class="h-full bg-gradient-to-r from-[#f59e0b] to-[#ef4444] transition-[width] duration-1000" :style="{ width: progressWidth }"></div></div>

    <div class="flex flex-1 flex-col overflow-hidden lg:flex-row">
      <div class="relative flex flex-1 items-center justify-center overflow-auto bg-slate-100 p-3 md:p-8">
        <div v-if="!selectedSection" class="w-full max-w-[1100px] rounded-xl bg-white p-4 shadow-xl md:p-7">
          <div class="relative top-6 z-20 mx-auto mb-0 flex h-16 w-full max-w-[520px] items-center justify-center rounded-lg bg-slate-900 text-3xl font-black tracking-[0.2em] text-white">STAGE</div>

          <div class="relative z-10 mx-auto h-[680px] max-w-[980px] overflow-hidden rounded-2xl bg-gradient-to-b from-white via-slate-50 to-slate-100">
            <div class="absolute left-[8%] top-[14%] rounded-full bg-white/80 px-3 py-1 text-xs font-bold text-slate-500">2F</div>
            <div class="absolute left-[22%] top-[14%] rounded-full bg-white/80 px-3 py-1 text-xs font-bold text-slate-500">1F</div>
            <div class="absolute left-1/2 top-[14%] -translate-x-1/2 -translate-y-[2px] rounded-full bg-white/80 px-3 py-1 text-xs font-bold text-slate-500">FLOOR</div>
            <div class="absolute right-[22%] top-[14%] rounded-full bg-white/80 px-3 py-1 text-xs font-bold text-slate-500">1F</div>
            <div class="absolute right-[8%] top-[14%] rounded-full bg-white/80 px-3 py-1 text-xs font-bold text-slate-500">2F</div>

            <button
              v-for="section in sections"
              :key="section.id"
              class="absolute -translate-x-1/2 -translate-y-1/2 border text-lg font-black shadow-sm transition-all hover:scale-105"
              :class="[toneClassMap[section.tone], section.floor === 'FLOOR' ? 'h-[68px] w-[88px] rounded-md' : 'h-[74px] w-[86px] [clip-path:polygon(18%_0%,82%_0%,100%_100%,0%_100%)]']"
              :style="{ left: `${section.x}%`, top: `${section.y}%`, transform: `translate(-50%, -50%) rotate(${section.angle}deg)` }"
              @click="goToSection(section)"
            >
              <span
                class="mx-auto block text-center"
                :class="section.floor === 'FLOOR' ? 'pt-5 text-3xl' : '[text-orientation:upright] [writing-mode:vertical-rl]'"
              >
                {{ section.id }}
              </span>
            </button>
          </div>

          <div class="mt-4 mb-3 flex items-center justify-between rounded border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
            <span class="font-semibold">공연장 총 좌석</span>
            <span class="font-black text-slate-800">{{ totalVenueSeats.toLocaleString() }}석</span>
          </div>
          <div class="grid grid-cols-2 gap-2 text-xs text-slate-600 md:grid-cols-5">
            <div v-for="legend in gradeLegend" :key="legend.grade" class="flex items-center rounded border border-slate-200 bg-white px-2 py-1.5">
              <span class="mr-2 inline-block h-3 w-3 rounded-full" :class="legend.tone === 'amber' ? 'bg-amber-400' : legend.tone === 'violet' ? 'bg-violet-400' : legend.tone === 'sky' ? 'bg-sky-400' : legend.tone === 'emerald' ? 'bg-emerald-400' : 'bg-slate-400'"></span>
              <span class="font-semibold">{{ legend.grade }}</span>
              <span class="ml-auto">{{ legend.price.toLocaleString() }}원</span>
            </div>
          </div>
        </div>

        <div v-else class="w-full max-w-[820px] rounded-xl bg-white p-4 shadow-xl md:p-8">
          <div class="mb-6 flex items-center justify-between border-b pb-4">
            <div>
              <h3 class="text-base font-bold md:text-xl">{{ selectedSection.id }}구역 좌석배치도</h3>
              <p class="text-xs text-slate-500 md:text-sm">{{ selectedSection.floor }} · {{ selectedSection.grade }}석 · {{ selectedSection.totalSeats.toLocaleString() }}석 · {{ selectedSection.price.toLocaleString() }}원</p>
            </div>
            <button class="text-xs text-slate-500 underline hover:text-slate-800 md:text-sm" @click="resetSection">전체 구역도로 돌아가기</button>
          </div>

          <div class="space-y-1.5 sm:space-y-2">
            <div
              v-for="row in selectedSection.rows"
              :key="row"
              class="grid items-center gap-1.5 sm:gap-2"
              :style="{ gridTemplateColumns: `28px repeat(${selectedSection.cols}, minmax(0, 1fr))` }"
            >
              <div class="text-center text-[10px] font-bold text-slate-500 sm:text-xs">{{ row }}</div>
              <button
                v-for="col in selectedSection.cols"
                :key="`${selectedSection.id}-${row}-${col}`"
                class="flex h-7 items-center justify-center rounded border text-[10px] transition-colors sm:h-8"
                :class="isHoldingSeat(`${selectedSection.id}-${row}-${col}`) ? 'cursor-wait border-slate-300 bg-slate-100 text-slate-400' : getSeatState(row, col).isOccupied ? 'cursor-not-allowed border-slate-300 bg-slate-200 text-slate-400' : getSeatState(row, col).isSelected ? 'border-[#f97316] bg-[#f97316] font-bold text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-orange-50'"
                :disabled="getSeatState(row, col).isOccupied || isHoldingSeat(`${selectedSection.id}-${row}-${col}`)"
                :title="`${row}열 ${col}번`"
                @click="toggleSeat(row, col)"
              >
                {{ col }}
              </button>
            </div>
          </div>

          <div class="mt-6 flex flex-wrap justify-center gap-4 text-xs text-slate-600 sm:space-x-6">
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded border border-[#f97316] bg-[#f97316]"></div>선택</div>
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded border border-slate-300 bg-white"></div>선택가능</div>
            <div class="flex items-center"><div class="mr-2 h-4 w-4 rounded border border-slate-300 bg-slate-200"></div>선택불가</div>
          </div>
        </div>
      </div>

      <div class="z-10 flex w-full flex-col border-t border-slate-200 bg-white shadow-[-4px_0_15px_rgba(0,0,0,0.05)] lg:w-[320px] lg:border-l lg:border-t-0">
        <div class="flex items-center justify-between bg-slate-800 p-4 font-bold text-white"><span>선택좌석</span><span class="text-[#f59e0b]">{{ selectedSeats.length }}석</span></div>

        <div class="flex-1 space-y-3 overflow-y-auto p-4">
          <div v-if="selectedSeats.length === 0" class="py-10 text-center text-sm text-slate-400">좌석을 선택해주세요.</div>
          <div
            v-for="seat in selectedSeats"
            :key="seat.id"
            class="group relative rounded border border-slate-200 bg-white p-3 shadow-sm"
          >
            <button class="absolute right-2 top-2 text-slate-400 hover:text-red-500" @click="removeSeat(seat)">
              <X :size="16" />
            </button>
            <div class="mb-1 flex items-center">
              <span class="mr-2 h-2 w-2 rounded-full" :class="seat.grade === 'VIP' ? 'bg-amber-500' : seat.grade === 'R' ? 'bg-violet-500' : seat.grade === 'S' ? 'bg-sky-500' : seat.grade === 'A' ? 'bg-emerald-500' : 'bg-slate-500'"></span>
              <span class="text-sm font-bold text-slate-800">{{ seat.grade }}</span>
            </div>
            <div class="mb-1 text-xs text-slate-600">{{ seat.section }}구역 {{ seat.row }}열 {{ seat.col }}번</div>
            <div class="text-right font-bold text-slate-800">{{ seat.price.toLocaleString() }}원</div>
          </div>
        </div>

        <div class="border-t border-slate-200 bg-slate-50 p-4">
          <div class="mb-4 flex items-center justify-between"><span class="text-sm font-bold text-slate-800">총 결제금액</span><span class="text-xl font-bold text-[#f97316]">{{ totalAmount.toLocaleString() }}원</span></div>
          <button
            class="w-full rounded py-4 text-lg font-bold transition-all"
            :class="selectedSeats.length > 0 ? 'bg-[#f97316] text-white shadow-md hover:bg-[#ea580c]' : 'cursor-not-allowed bg-slate-200 text-slate-500'"
            :disabled="selectedSeats.length === 0"
            @click="completeSeatSelection"
          >
            좌석선택 완료
          </button>
        </div>
      </div>
    </div>
    <div v-if="showExpiryWarningModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="w-full max-w-sm rounded bg-white p-6 text-center shadow-2xl">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
          <AlertCircle class="text-amber-600" :size="24" />
        </div>
        <h3 class="mb-2 text-lg font-bold">좌석 선택 시간이 곧 만료됩니다.</h3>
        <p class="mb-6 text-sm text-slate-600">
          30초 후 좌석 선택이 종료됩니다.
          <br />
          결제를 진행하거나 좌석을 확인해주세요.
        </p>
        <button class="w-full rounded bg-amber-600 py-3 font-bold text-white hover:bg-amber-700" @click="showExpiryWarningModal = false">확인</button>
      </div>
    </div>

    <div v-if="showConflictModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div class="w-full max-w-sm rounded bg-white p-6 text-center shadow-2xl">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100"><AlertCircle class="text-red-600" :size="24" /></div>
        <h3 class="mb-2 text-lg font-bold">이미 선택된 좌석입니다.</h3>
        <p class="mb-6 text-sm text-slate-600">
          선택하신 좌석이 다른 사용자에 의해
          <br />
          먼저 선점되었습니다.
          <br />
          다른 좌석을 선택해주세요.
        </p>
        <button class="w-full rounded bg-slate-800 py-3 font-bold text-white hover:bg-black" @click="showConflictModal = false">확인</button>
      </div>
    </div>
  </div>
</template>
