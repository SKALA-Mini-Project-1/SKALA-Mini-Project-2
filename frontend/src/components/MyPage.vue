<script setup lang="ts">
import { AlertCircle, LoaderCircle, Mail, Phone, QrCode, Trophy, User2, X } from 'lucide-vue-next';
import { computed, onMounted, ref } from 'vue';
import { apiRequest, ApiError } from '../services/api';
import { clearAuth, getAuthUser, getToken, setAuthUser } from '../services/auth';
import {
  cancelBookingHistory,
  ensureDummyBookingHistory,
  getBookingHistoryByUser
} from '../services/bookingHistory';
import type { BookingHistoryRecord } from '../types';

interface MyInfoResponse {
  userId: number;
  email: string;
  name: string;
  phone: string;
  fanScore: number;
  message: string;
}

const emit = defineEmits<{
  navigate: [path: string];
  loggedOut: [];
}>();

const showQr = ref(false);
const loadingProfile = ref(true);
const profileError = ref('');
const profileSaveError = ref('');
const profileSaveSuccess = ref('');
const myInfo = ref<MyInfoResponse | null>(null);
const bookings = ref<BookingHistoryRecord[]>([]);
const selectedBooking = ref<BookingHistoryRecord | null>(null);
const editingProfile = ref(false);
const savingProfile = ref(false);
const editName = ref('');
const editPhone = ref('');

const bookingCards = computed(() =>
  bookings.value.map((booking) => ({
    ...booking,
    statusLabel: booking.status === 'booked' ? '예매완료' : '취소완료',
    seatSummary:
      booking.seatLabels.length > 0
        ? booking.seatLabels.join(', ')
        : '좌석 정보 없음'
  }))
);

const fanTier = computed(() => {
  const score = myInfo.value?.fanScore ?? 0;
  if (score >= 4000) return 'GOLD';
  if (score >= 2500) return 'SILVER';
  if (score >= 1000) return 'BRONZE';
  return 'NEW';
});

const fanTierClass = computed(() => {
  if (fanTier.value === 'GOLD') return 'bg-amber-100 text-amber-700';
  if (fanTier.value === 'SILVER') return 'bg-slate-100 text-slate-700';
  if (fanTier.value === 'BRONZE') return 'bg-orange-100 text-orange-700';
  return 'bg-[#e8eff8] text-[#3f638a]';
});

const fanProgress = computed(() => {
  const score = Math.max(0, Math.min(5000, myInfo.value?.fanScore ?? 0));
  return Math.round((score / 5000) * 100);
});

const queuePriorityBoostMs = computed(() => {
  const score = Math.max(0, Math.min(5000, myInfo.value?.fanScore ?? 0));
  return score;
});

const loadBookings = () => {
  if (!myInfo.value) {
    bookings.value = [];
    return;
  }
  ensureDummyBookingHistory(myInfo.value.userId);
  bookings.value = getBookingHistoryByUser(myInfo.value.userId);
};

const loadMyInfo = async () => {
  const token = getToken();

  if (!token) {
    loadingProfile.value = false;
    profileError.value = '로그인이 필요합니다.';
    return;
  }

  loadingProfile.value = true;
  profileError.value = '';

  try {
    myInfo.value = await apiRequest<MyInfoResponse>('/api/users/me', {
      method: 'GET',
      token
    });
    editName.value = myInfo.value.name;
    editPhone.value = myInfo.value.phone;
    loadBookings();
  } catch (error) {
    profileError.value = error instanceof ApiError ? error.message : '사용자 정보를 불러오지 못했습니다.';

    if (error instanceof ApiError && error.status === 401) {
      clearAuth();
      emit('loggedOut');
    }
  } finally {
    loadingProfile.value = false;
  }
};

const startProfileEdit = () => {
  if (!myInfo.value) {
    return;
  }
  editName.value = myInfo.value.name;
  editPhone.value = myInfo.value.phone;
  profileSaveError.value = '';
  profileSaveSuccess.value = '';
  editingProfile.value = true;
};

const cancelProfileEdit = () => {
  if (myInfo.value) {
    editName.value = myInfo.value.name;
    editPhone.value = myInfo.value.phone;
  }
  profileSaveError.value = '';
  editingProfile.value = false;
};

const saveProfile = async () => {
  if (savingProfile.value || !myInfo.value) {
    return;
  }

  const name = editName.value.trim();
  const phone = editPhone.value.trim();

  if (!name) {
    profileSaveError.value = '이름은 필수입니다.';
    profileSaveSuccess.value = '';
    return;
  }

  const phonePattern = /^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$/;
  if (!phonePattern.test(phone)) {
    profileSaveError.value = '올바른 전화번호 형식이 아닙니다.';
    profileSaveSuccess.value = '';
    return;
  }

  const token = getToken();
  if (!token) {
    profileSaveError.value = '로그인이 필요합니다.';
    profileSaveSuccess.value = '';
    return;
  }

  savingProfile.value = true;
  profileSaveError.value = '';
  profileSaveSuccess.value = '';

  try {
    const updated = await apiRequest<MyInfoResponse>('/api/users/me', {
      method: 'PUT',
      token,
      body: JSON.stringify({ name, phone })
    });

    myInfo.value = updated;
    editName.value = updated.name;
    editPhone.value = updated.phone;
    setAuthUser({
      userId: updated.userId,
      email: updated.email,
      name: updated.name
    });
    editingProfile.value = false;
    profileSaveSuccess.value = '내 정보가 수정되었습니다.';
  } catch (error) {
    profileSaveError.value = error instanceof ApiError ? error.message : '내 정보 수정 중 오류가 발생했습니다.';
  } finally {
    savingProfile.value = false;
  }
};

const logout = async () => {
  const token = getToken();

  try {
    if (token) {
      await apiRequest<{ message: string; status: string }>('/api/users/logout', {
        method: 'POST',
        token
      });
    }
  } catch {
    // 서버 로그아웃 실패 시에도 클라이언트 세션은 제거
  } finally {
    clearAuth();
    emit('loggedOut');
    emit('navigate', '/login');
  }
};

const openQr = (booking: BookingHistoryRecord) => {
  selectedBooking.value = booking;
  showQr.value = true;
};

const cancelBooking = (bookingNumber: string) => {
  if (!myInfo.value) {
    return;
  }
  cancelBookingHistory(myInfo.value.userId, bookingNumber);
  loadBookings();
  if (selectedBooking.value?.bookingNumber === bookingNumber) {
    selectedBooking.value = getBookingHistoryByUser(myInfo.value.userId).find(
      (booking) => booking.bookingNumber === bookingNumber
    ) ?? null;
  }
};

onMounted(() => {
  const cachedUser = getAuthUser();
  if (!cachedUser && !getToken()) {
    loadingProfile.value = false;
    profileError.value = '로그인이 필요합니다.';
    return;
  }

  void loadMyInfo();
});
</script>

<template>
  <div class="mx-auto max-w-[1120px] p-3 sm:p-4 md:p-8">
    <div class="mb-6 rounded-2xl border border-[#ffd9b8] bg-white px-6 py-7 text-[#173451] shadow-sm">
      <p class="inline-block rounded-full bg-[#fff2e6] px-3 py-1 text-xs font-bold text-[#ff7a00]">MY FAIRLINE</p>
      <h2 class="mt-3 text-2xl font-black text-[#ff7a00] md:text-3xl">마이페이지</h2>
      <p class="mt-2 text-sm text-[#5e7691]">회원 정보, 팬점수, 예매 내역을 한 번에 관리하세요.</p>
    </div>

    <div v-if="loadingProfile" class="mb-6 flex items-center gap-2 rounded-xl border border-[#e2eaf3] bg-white p-4 text-sm text-[#4b6280] shadow-sm">
      <LoaderCircle class="animate-spin" :size="18" />
      사용자 정보를 불러오는 중입니다.
    </div>

    <div v-else-if="profileError" class="mb-6 rounded-xl border border-red-200 bg-red-50 p-4">
      <p class="text-sm font-bold text-red-600">{{ profileError }}</p>
      <button class="mt-3 h-10 rounded-lg bg-[#FF6B00] px-4 text-sm font-bold text-white" @click="emit('navigate', '/login')">
        로그인 페이지로 이동
      </button>
    </div>

    <div v-else-if="myInfo" class="grid gap-6 md:grid-cols-[340px_1fr]">
      <aside class="rounded-2xl border border-[#dbe6f2] bg-white p-5 shadow-sm md:sticky md:top-28 md:h-fit">
        <div class="mb-5 rounded-xl bg-[#f2f8ff] p-4">
          <div class="mb-2 flex items-center gap-2 text-[#204468]">
            <User2 :size="16" />
            <p class="text-sm font-extrabold">회원 정보</p>
          </div>

          <div v-if="!editingProfile" class="space-y-2 text-sm text-[#233c58]">
            <div class="flex items-center justify-between"><span class="text-[#5e7691]">이름</span><span class="font-semibold">{{ myInfo.name }}</span></div>
            <div class="flex items-center justify-between"><span class="text-[#5e7691]">이메일</span><span class="text-xs">{{ myInfo.email }}</span></div>
            <div class="flex items-center justify-between"><span class="text-[#5e7691]">전화번호</span><span>{{ myInfo.phone || '-' }}</span></div>
            <div class="flex items-center justify-between"><span class="text-[#5e7691]">회원번호</span><span>#{{ myInfo.userId }}</span></div>
          </div>

          <div v-else class="space-y-3 text-sm">
            <div>
              <label class="mb-1 block text-xs font-bold text-[#5f7894]">이름</label>
              <input
                v-model="editName"
                type="text"
                class="h-10 w-full rounded-lg border border-[#d5e2f1] bg-white px-3 text-[#244260] outline-none focus:border-[#FF6B00]"
                placeholder="이름을 입력하세요"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-bold text-[#5f7894]">전화번호</label>
              <input
                v-model="editPhone"
                type="tel"
                class="h-10 w-full rounded-lg border border-[#d5e2f1] bg-white px-3 text-[#244260] outline-none focus:border-[#FF6B00]"
                placeholder="010-1234-5678"
              />
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-[#e6edf6] bg-white p-4">
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2 text-[#203c5b]">
              <Trophy :size="16" />
              <span class="text-sm font-extrabold">팬 점수</span>
            </div>
            <span class="rounded-full px-2 py-1 text-xs font-bold" :class="fanTierClass">{{ fanTier }}</span>
          </div>
          <p class="text-2xl font-black text-[#ff7a00]">{{ myInfo.fanScore }}</p>
          <div class="mt-3 h-2 overflow-hidden rounded-full bg-[#e8eef5]">
            <div class="h-full rounded-full bg-gradient-to-r from-[#ff9a44] to-[#ff6b00]" :style="{ width: `${fanProgress}%` }"></div>
          </div>
          <p class="mt-2 text-xs text-[#6c83a0]">범위 0~5000점 (1점 = 1ms 우선순위 가중치)</p>
          <p class="mt-1 text-xs font-semibold text-[#395a7c]">현재 반영값: {{ queuePriorityBoostMs }}ms</p>
        </div>

        <p v-if="profileSaveError" class="mt-3 text-sm font-bold text-red-600">{{ profileSaveError }}</p>
        <p v-if="profileSaveSuccess" class="mt-3 text-sm font-bold text-emerald-600">{{ profileSaveSuccess }}</p>

        <div class="mt-4 grid grid-cols-2 gap-2">
          <button
            v-if="!editingProfile"
            class="col-span-2 h-10 rounded-lg bg-[#ff7a00] text-sm font-bold text-white hover:bg-[#e66d00]"
            @click="startProfileEdit"
          >
            내 정보 수정
          </button>
          <button
            v-if="editingProfile"
            class="h-10 rounded-lg bg-[#ff7a00] text-sm font-bold text-white hover:bg-[#e66d00] disabled:opacity-60"
            :disabled="savingProfile"
            @click="saveProfile"
          >
            {{ savingProfile ? '저장 중...' : '저장' }}
          </button>
          <button
            v-if="editingProfile"
            class="h-10 rounded-lg border border-[#d6e2ef] bg-white text-sm font-bold text-[#4e6782]"
            @click="cancelProfileEdit"
          >
            취소
          </button>
          <button
            class="col-span-2 h-10 rounded-lg border border-[#d6e2ef] bg-white text-sm font-bold text-[#4e6782] hover:bg-[#f4f8fc]"
            @click="logout"
          >
            로그아웃
          </button>
        </div>
      </aside>

      <section class="rounded-2xl border border-[#dbe6f2] bg-white p-5 shadow-sm md:p-6">
        <div class="mb-5 flex flex-col items-start gap-2 border-b border-[#edf2f8] pb-4 sm:flex-row sm:items-center sm:justify-between">
          <h3 class="text-xl font-black text-[#162f4d]">최근 예매 내역</h3>
          <span class="rounded-full bg-[#f3f8fe] px-3 py-1 text-xs font-semibold text-[#5e7691]">최신순 자동 정렬</span>
        </div>

        <div class="mb-5 grid gap-2 sm:grid-cols-3">
          <div class="rounded-xl bg-[#f6fbff] p-3 text-sm text-[#3a5674]">
            <p class="text-xs text-[#6a829b]">회원</p>
            <p class="mt-1 flex items-center gap-1 font-bold"><User2 :size="14" />{{ myInfo.name }}</p>
          </div>
          <div class="rounded-xl bg-[#f6fbff] p-3 text-sm text-[#3a5674]">
            <p class="text-xs text-[#6a829b]">연락처</p>
            <p class="mt-1 flex items-center gap-1 font-bold"><Phone :size="14" />{{ myInfo.phone || '미등록' }}</p>
          </div>
          <div class="rounded-xl bg-[#f6fbff] p-3 text-sm text-[#3a5674]">
            <p class="text-xs text-[#6a829b]">이메일</p>
            <p class="mt-1 flex items-center gap-1 truncate font-bold"><Mail :size="14" />{{ myInfo.email }}</p>
          </div>
        </div>

        <div v-if="bookingCards.length === 0" class="rounded-xl border border-dashed border-[#d9e2ec] bg-[#f8fbff] p-10 text-center text-sm text-[#6a829b]">
          아직 예매 내역이 없습니다.
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="booking in bookingCards"
            :key="booking.bookingNumber"
            class="rounded-2xl border border-[#e3ebf5] bg-[#fcfdff] p-4 transition-shadow hover:shadow-md md:p-5"
          >
            <div class="mb-4 flex flex-col items-start gap-2 border-b border-[#eaf0f7] pb-4 sm:flex-row sm:justify-between">
              <div>
                <div class="mb-1 text-xs font-semibold text-[#7d93aa]">예매번호 {{ booking.bookingNumber }}</div>
                <h4 class="text-base font-extrabold text-[#163452] md:text-lg">{{ booking.concertTitle }}</h4>
              </div>
              <span
                class="rounded-full px-3 py-1 text-xs font-bold"
                :class="booking.status === 'booked' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'"
              >
                {{ booking.statusLabel }}
              </span>
            </div>

            <div class="flex flex-col items-start justify-between gap-4 md:flex-row md:items-center">
              <div class="w-full space-y-1 text-sm">
                <div class="flex flex-col gap-0.5 sm:flex-row"><span class="w-16 text-[#6c83a0]">관람일시</span><span class="text-[#2a425c]">{{ booking.date }} {{ booking.session }}회차</span></div>
                <div class="flex flex-col gap-0.5 sm:flex-row"><span class="w-16 text-[#6c83a0]">공연장</span><span class="text-[#2a425c]">{{ booking.concertVenue }}</span></div>
                <div class="flex flex-col gap-0.5 sm:flex-row"><span class="w-16 text-[#6c83a0]">좌석정보</span><span class="text-[#2a425c]">{{ booking.seatSummary }}</span></div>
                <div class="flex flex-col gap-0.5 sm:flex-row"><span class="w-16 text-[#6c83a0]">결제금액</span><span class="font-extrabold text-[#163452]">{{ booking.totalAmount.toLocaleString() }}원</span></div>
              </div>

              <div v-if="booking.status === 'booked'" class="grid w-full grid-cols-1 gap-2 sm:grid-cols-2 md:flex md:w-auto md:space-x-2 md:gap-0">
                <button
                  class="flex items-center justify-center whitespace-nowrap rounded-lg bg-[#ff7a00] px-4 py-2 text-sm font-bold text-white hover:bg-[#e56000]"
                  @click="openQr(booking)"
                >
                  <QrCode :size="16" class="mr-2" />
                  모바일 티켓
                </button>
                <button
                  class="whitespace-nowrap rounded-lg border border-[#d7e2ee] bg-white px-4 py-2 text-sm font-bold text-[#4e6782] hover:bg-[#f5f9fd]"
                  @click="cancelBooking(booking.bookingNumber)"
                >
                  예매취소
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <div v-if="showQr && selectedBooking" class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4 backdrop-blur-sm" @click="showQr = false">
      <div class="w-full max-w-sm overflow-hidden rounded-2xl border border-[#ffd9bd] bg-white shadow-2xl" @click.stop>
        <div class="flex items-center justify-between bg-gradient-to-r from-[#ff7a00] to-[#ff9a44] p-4 text-white">
          <span class="font-bold">모바일 티켓</span>
          <button @click="showQr = false"><X :size="20" /></button>
        </div>
        <div class="flex flex-col items-center p-5 text-center sm:p-8">
          <h3 class="mb-1 text-lg font-extrabold text-[#193451]">{{ selectedBooking.concertTitle }}</h3>
          <p class="mb-2 text-sm text-[#62809f]">{{ selectedBooking.date }} {{ selectedBooking.session }}회차</p>
          <p class="mb-6 text-sm text-[#62809f]">{{ selectedBooking.seatLabels.join(', ') }}</p>

          <div class="mb-6 h-40 w-40 rounded-xl bg-gray-900 p-2 sm:h-48 sm:w-48">
            <div class="flex h-full w-full items-center justify-center bg-white"><QrCode :size="120" /></div>
          </div>

          <div class="flex animate-pulse items-center rounded-full bg-red-50 px-3 py-2 text-xs font-bold text-red-500">
            <AlertCircle :size="12" class="mr-1" />
            캡처된 화면으로는 입장이 불가합니다.
          </div>
        </div>
        <div class="border-t border-[#f1f1f1] bg-[#f8fbff] p-4 text-center text-xs text-[#8aa0b6]">입장 시 직원에게 QR코드를 보여주세요.</div>
      </div>
    </div>
  </div>
</template>
