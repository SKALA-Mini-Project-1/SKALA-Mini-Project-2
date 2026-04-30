<script setup lang="ts">
import { AlertCircle, X } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError } from '../services/api';
import { createBooking } from '../services/booking';
import {
  getSeatSectionDetailBySchedule,
  getSeatSectionSummaryBySchedule,
  holdSeat,
  holdSeatsBatch,
  leaveSeatScreen,
  type SeatMapItem,
  type SeatSectionSummaryItem
} from '../services/seat';
import type { Seat } from '../types';

const emit = defineEmits<{
  complete: [payload: { seats: Seat[]; bookingId: string }];
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
const seatIdMap = ref<Record<string, number>>({});
const sectionSummaryMap = ref<Record<string, SeatSectionSummaryItem>>({});
const isSeatSummaryLoading = ref(false);
const isSectionSeatsLoading = ref(false);
const loadedSectionId = ref<string | null>(null);
const shouldSkipLeaveOnUnmount = ref(false);
const hasSentLeave = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;
const concertId = computed(() => Number(props.concertId ?? 1));
const scheduleId = computed(() => (props.scheduleId ? Number(props.scheduleId) : null));
const isSeatMapLoading = computed(() => isSeatSummaryLoading.value || isSectionSeatsLoading.value);

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
  window.location.href = '/main';
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

const baseSections: SectionMeta[] = [...floorSections, ...firstFloorSections, ...secondFloorSections];

const toneClassMap: Record<SectionMeta['tone'], string> = {
  amber:   'border-2 border-amber-500 bg-amber-400 text-white hover:bg-amber-300 shadow-lg shadow-amber-200',
  violet:  'border-2 border-orange-600 bg-orange-500 text-white hover:bg-orange-400 shadow-lg shadow-orange-200',
  sky:     'border-2 border-yellow-400 bg-yellow-300 text-yellow-900 hover:bg-yellow-200 shadow-md shadow-yellow-100',
  emerald: 'border-2 border-emerald-500 bg-emerald-400 text-white hover:bg-emerald-300',
  slate:   'border-2 border-slate-400 bg-slate-300 text-slate-900 hover:bg-slate-200'
};

const resolveSectionToneFromGrade = (grade?: string): SectionMeta['tone'] => {
  if (grade === 'VIP') {
    return 'amber';
  }
  if (grade === 'R') {
    return 'violet';
  }
  if (grade === 'S') {
    return 'sky';
  }
  return 'slate';
};

const buildDynamicSection = (summary: SeatSectionSummaryItem, index: number): SectionMeta => ({
  id: summary.section,
  x: 16 + (index % 6) * 13.5,
  y: 22 + Math.floor(index / 6) * 12.5,
  angle: 0,
  floor: 'FLOOR',
  grade: summary.grade,
  price: summary.price,
  tone: resolveSectionToneFromGrade(summary.grade),
  totalSeats: summary.seatCount,
  rows: summary.rowCount,
  cols: summary.colCount
});

const sections = computed(() => {
  const summaries = Object.values(sectionSummaryMap.value);
  if (summaries.length === 0) {
    return baseSections;
  }

  const summaryById = sectionSummaryMap.value;
  const mergedKnownSections = baseSections
    .filter((section) => Boolean(summaryById[section.id]))
    .map((section) => {
      const summary = summaryById[section.id];
      return {
        ...section,
        grade: summary.grade || section.grade,
        price: summary.price ?? section.price,
        totalSeats: summary.seatCount ?? section.totalSeats,
        rows: summary.rowCount ?? section.rows,
        cols: summary.colCount ?? section.cols,
        tone: resolveSectionToneFromGrade(summary.grade || section.grade)
      };
    });

  const knownSectionIds = new Set(baseSections.map((section) => section.id));
  const dynamicSections = summaries
    .filter((summary) => !knownSectionIds.has(summary.section))
    .map((summary, index) => buildDynamicSection(summary, index));

  return [...mergedKnownSections, ...dynamicSections];
});

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

  void loadSeatSummary();
  window.addEventListener('pagehide', leaveSeatOnExit);
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
  window.removeEventListener('pagehide', leaveSeatOnExit);
  void leaveSeatOnExit();
});

const formatTime = (seconds: number) => {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
};

const buildSeatKey = (section: string, row: number, col: number) => `${section}-${row}-${col}`;
const getEffectiveSeatStatus = (seat: Pick<SeatMapItem, 'status' | 'displayStatus'>) => seat.displayStatus ?? seat.status;
const isSeatBlocked = (status?: string) => status === 'RESERVED' || status === 'HELD_BY_OTHER';

const applySeatAccessTtl = (seatAccessTtlSeconds?: number) => {
  const serverTtl = Math.floor(Number(seatAccessTtlSeconds ?? 0));
  if (Number.isFinite(serverTtl) && serverTtl > 0) {
    initialTimeLeft.value = serverTtl;
    timeLeft.value = serverTtl;
    hasShownExpiryWarning.value = false;
    showExpiryWarningModal.value = false;
  }
};

const loadSeatSummary = async () => {
  if (!scheduleId.value) {
    return;
  }

  isSeatSummaryLoading.value = true;
  try {
    const response = await getSeatSectionSummaryBySchedule(scheduleId.value);
    const nextSummaryMap = response.sections.reduce<Record<string, SeatSectionSummaryItem>>((acc, section) => {
      acc[section.section] = section;
      return acc;
    }, {});

    sectionSummaryMap.value = nextSummaryMap;
    applySeatAccessTtl(response.seatAccessTtlSeconds);

  } catch (error) {
    console.error('좌석 구역 요약 조회 실패', error);
  } finally {
    isSeatSummaryLoading.value = false;
  }
};

const loadSectionSeatMap = async (sectionId: string) => {
  if (!scheduleId.value) {
    return;
  }

  isSectionSeatsLoading.value = true;
  try {
    const response = await getSeatSectionDetailBySchedule(scheduleId.value, sectionId);
    const nextStatusMap = { ...seatStatusMap.value };
    const nextSeatIdMap = { ...seatIdMap.value };

    response.seats.forEach((seat: SeatMapItem) => {
      const key = buildSeatKey(seat.section, seat.rowNumber, seat.seatNumber);
      nextStatusMap[key] = getEffectiveSeatStatus(seat);
      nextSeatIdMap[key] = seat.seatId;
    });

    seatStatusMap.value = nextStatusMap;
    seatIdMap.value = nextSeatIdMap;
    loadedSectionId.value = sectionId;
    applySeatAccessTtl(response.seatAccessTtlSeconds);

  } catch (error) {
    console.error('좌석 구역 상세 조회 실패', error);
    alert(error instanceof Error ? error.message : '좌석 구역 정보를 불러오지 못했습니다.');
  } finally {
    isSectionSeatsLoading.value = false;
  }
};

const progressWidth = computed(() => {
  const base = Math.max(1, initialTimeLeft.value);
  return `${(timeLeft.value / base) * 100}%`;
});
const totalAmount = computed(() => selectedSeats.value.reduce((sum, seat) => sum + seat.price, 0));

const currentSectionSeats = computed(() => {
  if (!selectedSection.value) {
    return [] as Array<{ row: number; col: number; id: string; status: string; isOccupied: boolean; isSelected: boolean }>;
  }
  if (loadedSectionId.value !== selectedSection.value.id) {
    return [] as Array<{ row: number; col: number; id: string; status: string; isOccupied: boolean; isSelected: boolean }>;
  }

  const { id, rows, cols } = selectedSection.value;
  const seats: Array<{ row: number; col: number; id: string; status: string; isOccupied: boolean; isSelected: boolean }> = [];

  for (let row = 1; row <= rows; row += 1) {
    for (let col = 1; col <= cols; col += 1) {
      const seatId = buildSeatKey(id, row, col);
      const status = seatStatusMap.value[seatId] ?? 'AVAILABLE';
      const isOccupied = isSeatBlocked(status);
      const isSelected = selectedSeats.value.some((seat) => seat.id === seatId);
      seats.push({ row, col, id: seatId, status, isOccupied, isSelected });
    }
  }

  return seats;
});

const seatStateMap = computed(() => {
  const map = new Map<string, { status: string; isOccupied: boolean; isSelected: boolean }>();

  currentSectionSeats.value.forEach((seat) => {
    map.set(seat.id, { status: seat.status, isOccupied: seat.isOccupied, isSelected: seat.isSelected });
  });

  return map;
});

const getSeatState = (row: number, col: number) => {
  if (!selectedSection.value) {
    return { status: 'AVAILABLE', isOccupied: false, isSelected: false };
  }

  return seatStateMap.value.get(`${selectedSection.value.id}-${row}-${col}`) ?? { status: 'AVAILABLE', isOccupied: false, isSelected: false };
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

const goToSection = async (section: SectionMeta) => {
  selectedSection.value = section;
  if (loadedSectionId.value === section.id) {
    return;
  }
  await loadSectionSeatMap(section.id);
};

const resetSection = () => {
  selectedSection.value = null;
};

const toggleSeat = async (row: number, col: number) => {
  if (!selectedSection.value) {
    return;
  }

  const seatId = buildSeatKey(selectedSection.value.id, row, col);
  const isOccupied = isSeatBlocked(seatStatusMap.value[seatId]);

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
      if (selectedSection.value) {
        void loadSectionSeatMap(selectedSection.value.id);
      }
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

  sections.value.forEach((section) => {
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

const totalVenueSeats = computed(() => {
  const summarizedSeatCount = Object.values(sectionSummaryMap.value)
    .reduce((sum, section) => sum + section.seatCount, 0);
  if (summarizedSeatCount > 0) {
    return summarizedSeatCount;
  }
  return sections.value.reduce((sum, section) => sum + section.totalSeats, 0);
});

const completeSeatSelection = async () => {
  if (isSeatMapLoading.value) {
    return;
  }

  if (selectedSeats.value.length === 0) {
    return;
  }

  if (!concertId.value) {
    alert('공연 정보가 없어 예매를 진행할 수 없습니다.');
    return;
  }

  const resolvedSeatMappings = selectedSeats.value.map((seat) => {
    const key = buildSeatKey(seat.section ?? '', seat.row, seat.col);
    const resolvedSeatId = seatIdMap.value[key];
    return {
      key,
      resolvedSeatId,
      seat
    };
  });

  const seatIds = resolvedSeatMappings
    .map((item) => item.resolvedSeatId)
    .filter((seatId): seatId is number => Number.isFinite(seatId));

  const missingMappings = resolvedSeatMappings.filter((item) => !Number.isFinite(item.resolvedSeatId));

  if (seatIds.length !== selectedSeats.value.length) {
    console.error('[SeatSelection] seatId mapping failed', {
      concertId: concertId.value,
      scheduleId: scheduleId.value,
      missingMappings
    });
    alert('좌석 식별 정보를 찾지 못했습니다. 잠시 후 다시 시도해주세요.');
    return;
  }

  try {
    await holdSeatsBatch(concertId.value, seatIds);
    const booking = await createBooking(concertId.value, seatIds);

    shouldSkipLeaveOnUnmount.value = true;
    emit('complete', {
      seats: selectedSeats.value,
      bookingId: booking.bookingId
    });
  } catch (error) {
    alert(error instanceof Error ? error.message : '예매 생성 중 오류가 발생했습니다.');
  }
};
</script>

<template>
  <div class="flex min-h-[calc(100vh-140px)] flex-col bg-[#fff8f0]">
    <!-- 타이머 바 -->
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
    <div class="h-1 w-full bg-slate-800">
      <div class="h-full bg-gradient-to-r from-[#f59e0b] to-[#ef4444] transition-[width] duration-1000" :style="{ width: progressWidth }"></div>
    </div>

    <div class="flex flex-1 flex-col overflow-hidden lg:flex-row">

      <!-- ─── 좌석 맵 영역 ─── -->
      <div class="relative flex flex-1 items-center justify-center overflow-auto bg-[#fff8f0] p-3 md:p-6">

        <!-- ── 구역 전체 지도 ── -->
        <div v-if="!selectedSection" class="w-full max-w-[1060px]">

          <!-- STAGE -->
          <div class="relative z-20 mx-auto mb-0 flex w-full max-w-[500px] items-center justify-center rounded-t-2xl bg-gradient-to-r from-orange-500 via-amber-400 to-orange-500 py-3 text-2xl font-black tracking-[0.28em] text-white shadow-lg">
            S T A G E
          </div>

          <!-- 공연장 컨테이너: 밝은 크림 배경 + 동심 구역선 -->
          <div class="relative mx-auto h-[660px] max-w-[960px] overflow-hidden rounded-b-[60px] rounded-t-none border border-orange-200 bg-gradient-to-b from-orange-50 to-amber-50 shadow-inner">

            <!-- 상단 무대 잔광 -->
            <div class="pointer-events-none absolute inset-x-0 top-0 h-40 bg-gradient-to-b from-amber-100/60 to-transparent"></div>

            <!-- 구역 경계 원호 (장식) -->
            <div
              class="pointer-events-none absolute rounded-full border-2 border-orange-200/70"
              style="width:60%;height:60%;left:20%;top:-4%;"
            ></div>
            <div
              class="pointer-events-none absolute rounded-full border-2 border-orange-100/80"
              style="width:94%;height:94%;left:3%;top:-4%;"
            ></div>

            <!-- 구역 레이블 -->
            <div class="absolute left-[7%]  top-[12%] rounded-full bg-white/70 px-2.5 py-0.5 text-[11px] font-bold text-orange-400 shadow-sm">2F</div>
            <div class="absolute left-[21%] top-[12%] rounded-full bg-white/70 px-2.5 py-0.5 text-[11px] font-bold text-orange-500 shadow-sm">1F</div>
            <div class="absolute left-1/2 top-[12%] -translate-x-1/2 rounded-full bg-white/70 px-2.5 py-0.5 text-[11px] font-bold text-amber-600 shadow-sm">FLOOR</div>
            <div class="absolute right-[21%] top-[12%] rounded-full bg-white/70 px-2.5 py-0.5 text-[11px] font-bold text-orange-500 shadow-sm">1F</div>
            <div class="absolute right-[7%]  top-[12%] rounded-full bg-white/70 px-2.5 py-0.5 text-[11px] font-bold text-orange-400 shadow-sm">2F</div>

            <!-- 구역 버튼
                 ✅ clip-path 제거, rotate 제거 → 마름모꼴 없이 자연스러운 직사각형 배치 -->
            <button
              v-for="section in sections"
              :key="section.id"
              class="absolute -translate-x-1/2 -translate-y-1/2 cursor-pointer font-black transition-all duration-150 hover:z-10 hover:scale-110 hover:brightness-110"
              :class="[
                toneClassMap[section.tone],
                section.floor === 'FLOOR'
                  ? 'h-[70px] w-[88px] rounded-xl text-2xl'
                  : section.floor === '1F'
                    ? 'h-[58px] w-[72px] rounded-lg text-xl'
                    : 'h-[52px] w-[65px] rounded-lg text-lg'
              ]"
              :disabled="isSeatSummaryLoading"
              :style="{ left: `${section.x}%`, top: `${section.y}%` }"
              @click="goToSection(section)"
            >
              <span class="block text-center leading-none">{{ section.id }}</span>
            </button>
          </div>

          <div v-if="isSeatSummaryLoading" class="mt-4 rounded-lg border border-orange-200 bg-white px-4 py-3 text-center text-sm font-semibold text-orange-600 shadow-sm">
            좌석 구역 정보를 불러오는 중입니다...
          </div>

          <!-- 총 좌석 수 -->
          <div class="mt-4 mb-3 flex items-center justify-between rounded-lg border border-orange-200 bg-white px-4 py-2 text-xs text-slate-600 shadow-sm">
            <span class="font-semibold">공연장 총 좌석</span>
            <span class="font-black text-orange-600">{{ totalVenueSeats.toLocaleString() }}석</span>
          </div>

          <!-- 등급 범례 -->
          <div class="grid grid-cols-2 gap-2 text-xs md:grid-cols-5">
            <div
              v-for="legend in gradeLegend"
              :key="legend.grade"
              class="flex items-center rounded-lg border border-orange-100 bg-white px-3 py-2 shadow-sm"
            >
              <span
                class="mr-2 inline-block h-3 w-3 rounded-full"
                :class="
                  legend.tone === 'amber'  ? 'bg-amber-400' :
                  legend.tone === 'violet' ? 'bg-orange-500' :
                  legend.tone === 'sky'    ? 'bg-yellow-300' :
                  'bg-slate-300'
                "
              ></span>
              <span class="font-semibold text-slate-700">{{ legend.grade }}</span>
              <span class="ml-auto text-slate-500">{{ legend.price.toLocaleString() }}원</span>
            </div>
          </div>
        </div>

        <!-- ── 구역 내 개별 좌석 ── -->
        <div v-else class="w-full max-w-[820px] rounded-2xl border border-orange-200 bg-white p-4 shadow-xl md:p-8">
          <div class="mb-5 flex items-center justify-between border-b border-orange-100 pb-4">
            <div>
              <h3 class="text-base font-bold text-slate-800 md:text-xl">{{ selectedSection.id }}구역 좌석배치도</h3>
              <p class="text-xs text-slate-500 md:text-sm">
                {{ selectedSection.floor }} · {{ selectedSection.grade }}석 ·
                {{ selectedSection.totalSeats.toLocaleString() }}석 ·
                {{ selectedSection.price.toLocaleString() }}원
              </p>
            </div>
            <button class="rounded-lg border border-orange-200 bg-orange-50 px-3 py-1.5 text-xs font-semibold text-orange-600 hover:bg-orange-100 md:text-sm" @click="resetSection">
              ← 전체 구역도
            </button>
          </div>

          <!-- 무대 방향 표시 -->
          <div class="mb-5 text-center">
            <div class="mx-auto inline-block rounded-lg bg-gradient-to-r from-orange-500 to-amber-400 px-10 py-1.5 text-xs font-black tracking-widest text-white shadow-md">
              STAGE
            </div>
          </div>

          <!-- 좌석 그리드 -->
          <div v-if="isSectionSeatsLoading" class="rounded-xl border border-orange-100 bg-orange-50 px-4 py-12 text-center text-sm font-semibold text-orange-600">
            {{ selectedSection.id }}구역 좌석 정보를 불러오는 중입니다...
          </div>
          <div v-else-if="currentSectionSeats.length === 0" class="rounded-xl border border-orange-100 bg-orange-50 px-4 py-12 text-center text-sm font-semibold text-orange-600">
            {{ selectedSection.id }}구역 좌석 정보가 없습니다.
          </div>
          <div v-else class="space-y-1.5 sm:space-y-2">
            <div
              v-for="row in selectedSection.rows"
              :key="row"
              class="grid items-center gap-1 sm:gap-1.5"
              :style="{ gridTemplateColumns: `28px repeat(${selectedSection.cols}, minmax(0, 1fr))` }"
            >
              <div class="text-center text-[10px] font-bold text-slate-400 sm:text-xs">{{ row }}</div>
              <button
                v-for="col in selectedSection.cols"
                :key="`${selectedSection.id}-${row}-${col}`"
                class="flex h-7 items-center justify-center rounded border text-[10px] transition-colors sm:h-8"
                :class="
                  isHoldingSeat(`${selectedSection.id}-${row}-${col}`)
                    ? 'cursor-wait border-orange-200 bg-orange-100 text-orange-300'
                    : getSeatState(row, col).status === 'HELD_BY_OTHER'
                      ? 'cursor-not-allowed border-amber-200 bg-amber-50 text-amber-400'
                      : getSeatState(row, col).status === 'RESERVED'
                        ? 'cursor-not-allowed border-slate-300 bg-slate-300 text-slate-500'
                      : getSeatState(row, col).isOccupied
                      ? 'cursor-not-allowed border-slate-200 bg-slate-100 text-slate-300'
                      : getSeatState(row, col).isSelected
                        ? 'border-orange-500 bg-orange-500 font-bold text-white shadow-md shadow-orange-200'
                        : 'border-orange-200 bg-white text-slate-600 hover:border-orange-400 hover:bg-orange-50'
                "
                :disabled="getSeatState(row, col).isOccupied || isHoldingSeat(`${selectedSection.id}-${row}-${col}`)"
                :title="`${row}열 ${col}번${getSeatState(row, col).status === 'HELD_BY_OTHER' ? ' · 다른 사용자가 선점 중' : getSeatState(row, col).status === 'RESERVED' ? ' · 예매 완료' : ''}`"
                @click="toggleSeat(row, col)"
              >
                {{ col }}
              </button>
            </div>
          </div>

          <!-- 범례 -->
          <div class="mt-6 flex flex-wrap justify-center gap-5 text-xs text-slate-500">
            <div class="flex items-center gap-1.5">
              <div class="h-4 w-4 rounded border border-orange-500 bg-orange-500"></div>선택
            </div>
            <div class="flex items-center gap-1.5">
              <div class="h-4 w-4 rounded border border-orange-200 bg-white"></div>선택가능
            </div>
            <div class="flex items-center gap-1.5">
              <div class="h-4 w-4 rounded border border-amber-200 bg-amber-50"></div>다른 사용자 선점중
            </div>
            <div class="flex items-center gap-1.5">
              <div class="h-4 w-4 rounded border border-slate-300 bg-slate-300"></div>예매 완료
            </div>
          </div>
        </div>
      </div>

      <!-- ─── 우측 선택 사이드바 ─── -->
      <div class="z-10 flex w-full flex-col border-t border-orange-200 bg-white shadow-[-4px_0_12px_rgba(0,0,0,0.06)] lg:w-[320px] lg:border-l lg:border-t-0">
        <div class="flex items-center justify-between bg-orange-500 p-4 font-bold text-white">
          <span>선택 좌석</span>
          <span class="rounded-full bg-white/20 px-3 py-0.5 text-sm">{{ selectedSeats.length }}석</span>
        </div>

        <div class="flex-1 space-y-3 overflow-y-auto p-4">
          <div v-if="selectedSeats.length === 0" class="py-10 text-center text-sm text-slate-400">
            좌석을 선택해주세요.
          </div>
          <div
            v-for="seat in selectedSeats"
            :key="seat.id"
            class="group relative rounded-xl border border-orange-100 bg-orange-50 p-3"
          >
            <button class="absolute right-2 top-2 text-slate-400 hover:text-red-500" @click="removeSeat(seat)">
              <X :size="16" />
            </button>
            <div class="mb-1 flex items-center">
              <span
                class="mr-2 h-2 w-2 rounded-full"
                :class="seat.grade === 'VIP' ? 'bg-amber-400' : seat.grade === 'R' ? 'bg-orange-500' : 'bg-yellow-400'"
              ></span>
              <span class="text-sm font-bold text-slate-800">{{ seat.grade }}</span>
            </div>
            <div class="mb-1 text-xs text-slate-500">{{ seat.section }}구역 {{ seat.row }}열 {{ seat.col }}번</div>
            <div class="text-right font-bold text-orange-600">{{ seat.price.toLocaleString() }}원</div>
          </div>
        </div>

        <div class="border-t border-orange-100 bg-orange-50 p-4">
          <div class="mb-4 flex items-center justify-between">
            <span class="text-sm font-bold text-slate-700">총 결제금액</span>
            <span class="text-xl font-bold text-orange-600">{{ totalAmount.toLocaleString() }}원</span>
          </div>
          <button
            class="w-full rounded-xl py-4 text-lg font-bold transition-all"
            :class="
              selectedSeats.length > 0 && !isSeatMapLoading
                ? 'bg-orange-500 text-white shadow-lg shadow-orange-200 hover:bg-orange-600'
                : 'cursor-not-allowed bg-slate-200 text-slate-400'
            "
            :disabled="selectedSeats.length === 0 || isSeatMapLoading"
            @click="completeSeatSelection"
          >
            {{ isSeatMapLoading ? '좌석 정보 로딩 중...' : '좌석선택 완료' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ─── 만료 경고 모달 ─── -->
    <div v-if="showExpiryWarningModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 text-center shadow-2xl">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
          <AlertCircle class="text-amber-500" :size="24" />
        </div>
        <h3 class="mb-2 text-lg font-bold text-slate-800">좌석 선택 시간이 곧 만료됩니다.</h3>
        <p class="mb-6 text-sm text-slate-500">
          30초 후 좌석 선택이 종료됩니다.<br />결제를 진행하거나 좌석을 확인해주세요.
        </p>
        <button class="w-full rounded-xl bg-orange-500 py-3 font-bold text-white hover:bg-orange-600" @click="showExpiryWarningModal = false">확인</button>
      </div>
    </div>

    <!-- ─── 좌석 충돌 모달 ─── -->
    <div v-if="showConflictModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div class="w-full max-w-sm rounded-2xl bg-white p-6 text-center shadow-2xl">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
          <AlertCircle class="text-red-500" :size="24" />
        </div>
        <h3 class="mb-2 text-lg font-bold text-slate-800">이미 선택된 좌석입니다.</h3>
        <p class="mb-6 text-sm text-slate-500">
          선택하신 좌석이 다른 사용자에 의해<br />먼저 선점되었습니다.<br />다른 좌석을 선택해주세요.
        </p>
        <button class="w-full rounded-xl bg-slate-800 py-3 font-bold text-white hover:bg-black" @click="showConflictModal = false">확인</button>
      </div>
    </div>
  </div>
</template>
