<script setup lang="ts">
import { Check, Home, QrCode, Smartphone } from "lucide-vue-next";
import { computed, onMounted, ref } from "vue";
import type { BookingData } from "../types";
import { getToken } from "../services/auth";

const emit = defineEmits<{
  navigate: [path: string];
}>();

const props = defineProps<{
  bookingData?: BookingData | null;
}>();

const bookingData = ref<BookingData | null>(props.bookingData ?? null);
const errorMessage = ref<string>("");
const totalAmountFromApi = ref(0);

type BookingDetailApiResponse = {
  bookingId: string;
  totalPrice: number;
  seats: Array<{
    seatId: number;
    section: string | null;
    rowNumber: number | null;
    seatNumber: number | null;
    grade: string | null;
    price: number | string | null;
  }>;
  concert: {
    concertName: string | null;
    showDateTime: string | null;
    venue: string | null;
  } | null;
};

const formatDate = (iso: string | null) => {
  if (!iso) return "-";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "-";
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

const formatTime = (iso: string | null) => {
  if (!iso) return "-";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "-";
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
};

const totalAmount = computed(() => {
  if (totalAmountFromApi.value > 0) return totalAmountFromApi.value;
  if (!bookingData.value) return 0;
  return bookingData.value.seats.reduce((sum, seat) => sum + seat.price, 0);
});

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const bookingId = params.get("bookingId") ?? "";
  if (!bookingId) {
    if (bookingData.value) {
      return;
    }
    errorMessage.value = "예매 정보를 불러올 수 없습니다. (bookingId 누락)";
    return;
  }

  const token = getToken();
  if (!token) {
    errorMessage.value = "로그인 정보가 없어 예매 정보를 조회할 수 없습니다.";
    return;
  }

  try {
    const res = await fetch(`/api/bookings/${bookingId}`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(text || `booking fetch failed: ${res.status}`);
    }
    const detail = (await res.json()) as BookingDetailApiResponse;

    totalAmountFromApi.value = Number(detail.totalPrice ?? 0);

    const showDateTime = detail.concert?.showDateTime ?? null;
    const mappedSeats = (detail.seats ?? []).map((seat) => ({
      id: String(seat.seatId),
      section: seat.section,
      row: seat.rowNumber ?? 0,
      col: seat.seatNumber ?? 0,
      grade: seat.grade ?? "-",
      price: Number(seat.price ?? 0)
    }));

    bookingData.value = {
      bookingId: detail.bookingId,
      bookingNumber: detail.bookingId,
      concertId: null,
      concertCode: null,
      scheduleId: null,
      concertTitle: detail.concert?.concertName ?? "선택한 공연",
      concertVenue: detail.concert?.venue ?? "-",
      date: formatDate(showDateTime),
      session: formatTime(showDateTime),
      seats: mappedSeats
    };
  } catch (e: any) {
    errorMessage.value = e?.message ?? "예매 정보를 불러오는 중 오류가 발생했습니다.";
  }
});
</script>

<template>
  <div class="mx-auto max-w-[800px] p-3 sm:p-4 md:p-8">
    <div v-if="errorMessage" class="mb-6 rounded-sm border border-red-200 bg-red-50 p-4 text-sm text-red-600">
      {{ errorMessage }}
    </div>
    <div v-else-if="bookingData">
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
          <span class="text-xs text-[#666] sm:text-sm">예매번호: <span class="ml-1 text-base font-bold text-[#FF6B00] sm:text-lg">{{ bookingData.bookingNumber || '-' }}</span></span>
        </div>

        <div class="p-4 sm:p-6">
          <table class="w-full text-xs sm:text-sm">
            <tbody class="divide-y divide-[#f0f0f0]">
              <tr><th class="w-32 py-3 text-left font-normal text-[#666]">공연명</th><td class="py-3 font-bold text-[#333]">{{ bookingData.concertTitle || '선택한 공연' }}</td></tr>
              <tr><th class="py-3 text-left font-normal text-[#666]">일시</th><td class="py-3 text-[#333]">{{ bookingData.date }} {{ bookingData.session }}</td></tr>
              <tr><th class="py-3 text-left font-normal text-[#666]">장소</th><td class="py-3 text-[#333]">{{ bookingData.concertVenue || '-' }}</td></tr>
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
          @click="emit('navigate', '/main')"
        >
          <Home :size="20" />
          <span>메인으로 돌아가기</span>
        </button>
      </div>
    </div>
    <div v-else class="py-16 text-center text-sm text-[#666]">예매 정보를 불러오는 중입니다...</div>
    </div>
</template>
