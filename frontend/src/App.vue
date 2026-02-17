<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import Header from './components/Header.vue';
import MainPage from './components/MainPage.vue';
import ConcertListPage from './components/ConcertListPage.vue';
import ConcertDetail from './components/ConcertDetail.vue';
import ConcertOpenPage from './components/ConcertOpenPage.vue';
import QueueScreen from './components/QueueScreen.vue';
import SeatSelection from './components/SeatSelection.vue';
import PaymentScreen from './components/PaymentScreen.vue';
import BookingConfirmation from './components/BookingConfirmation.vue';
import PaymentRedirectSuccess from './components/PaymentRedirectSuccess.vue';
import PaymentRedirectFail from './components/PaymentRedirectFail.vue';
import MyPage from './components/MyPage.vue';
import ServerError from './components/ServerError.vue';
import SoldOut from './components/SoldOut.vue';
import LoginPage from './components/LoginPage.vue';
import SignupPage from './components/SignupPage.vue';
import { concerts, getConcertById } from './data/concerts';
import type { BookingData, Seat } from './types';
import { apiRequest } from './services/api';
import { clearAuth, getToken, isLoggedIn } from './services/auth';

const bookingData = reactive<BookingData>({
  concertId: null,
  concertTitle: null,
  concertVenue: null,
  date: null,
  session: null,
  seats: []
});

const authState = ref(isLoggedIn());
const currentPath = ref(window.location.pathname || '/main');
const currentSearch = ref(window.location.search || '');
const pendingPath = ref<string | null>(null);
const pendingBooking = ref<{
  concertId: string;
  concertTitle: string;
  concertVenue: string;
  date: string;
  session: string;
} | null>(null);

const validPaths = new Set([
  '/main',
  '/concerts',
  '/concert/detail',
  '/concert/open',
  '/concert/queue',
  '/concert/seat',
  '/concert/payment',
  '/concert/confirm',
  '/payments/success',
  '/payments/fail',
  '/mypage',
  '/login',
  '/signup',
  '/error',
  '/soldout'
]);

const protectedPaths = new Set([
  '/mypage',
  '/concert/queue',
  '/concert/seat',
  '/concert/payment',
  '/concert/confirm'
]);

const normalizedPath = computed(() => {
  if (currentPath.value === '/') {
    return '/main';
  }

  if (validPaths.has(currentPath.value)) {
    return currentPath.value;
  }

  return '/main';
});

const selectedConcertId = computed(() => {
  const params = new URLSearchParams(currentSearch.value);
  return params.get('concert');
});

const selectedConcert = computed(() => getConcertById(selectedConcertId.value));

const setPath = (path: string, replace = false) => {
  const url = new URL(path, window.location.origin);
  const next = `${url.pathname}${url.search}`;

  if (replace) {
    window.history.replaceState({}, '', next);
  } else {
    window.history.pushState({}, '', next);
  }

  currentPath.value = url.pathname;
  currentSearch.value = url.search;
};

const redirectToLogin = (path: string, search = '', replace = false) => {
  pendingPath.value = `${path}${search}`;
  setPath('/login', replace);
};

const navigate = (path: string) => {
  const url = new URL(path, window.location.origin);

  if (protectedPaths.has(url.pathname) && !authState.value) {
    redirectToLogin(url.pathname, url.search);
    return;
  }

  setPath(path);
};

const openConcert = (concertId: string) => {
  navigate(`/concert/detail?concert=${concertId}`);
};

const handlePopState = () => {
  const path = window.location.pathname || '/main';
  const search = window.location.search || '';

  if (protectedPaths.has(path) && !authState.value) {
    redirectToLogin(path, search, true);
    return;
  }

  currentPath.value = path;
  currentSearch.value = search;
};

const handleBookingStart = (date: string, session: string) => {
  const concertId = selectedConcert.value.id;
  const concertTitle = `${selectedConcert.value.title} ${selectedConcert.value.subtitle}`;
  const concertVenue = selectedConcert.value.venue;

  if (!authState.value) {
    pendingBooking.value = { concertId, concertTitle, concertVenue, date, session };
    redirectToLogin('/concert/detail', `?concert=${concertId}`);
    return;
  }

  bookingData.concertId = selectedConcert.value.id;
  bookingData.concertTitle = concertTitle;
  bookingData.concertVenue = concertVenue;
  bookingData.date = date;
  bookingData.session = session;
  bookingData.seats = [];
  navigate('/concert/queue');
};

const handleQueueComplete = () => {
  navigate('/concert/seat');
};

const handleSeatComplete = (seats: Seat[]) => {
  bookingData.seats = seats;
  navigate('/concert/payment');
};

const handlePaymentComplete = () => {
  navigate('/concert/confirm');
};

const handleLogout = async () => {
  try {
    const token = getToken();
    if (token) {
      await apiRequest<{ message: string; status: string }>('/api/users/logout', {
        method: 'POST',
        token
      });
    }
  } catch {
    // 서버 로그아웃 실패여도 클라이언트 세션은 제거
  } finally {
    clearAuth();
    authState.value = false;
    if (protectedPaths.has(normalizedPath.value)) {
      navigate('/login');
    }
  }
};

const handleLoggedIn = () => {
  authState.value = true;

  if (pendingBooking.value) {
    bookingData.concertId = pendingBooking.value.concertId;
    bookingData.concertTitle = pendingBooking.value.concertTitle;
    bookingData.concertVenue = pendingBooking.value.concertVenue;
    bookingData.date = pendingBooking.value.date;
    bookingData.session = pendingBooking.value.session;
    pendingBooking.value = null;
    pendingPath.value = null;
    navigate('/concert/queue');
    return;
  }

  if (pendingPath.value) {
    const targetPath = pendingPath.value;
    pendingPath.value = null;
    navigate(targetPath);
    return;
  }

  navigate('/mypage');
};

const handleLoggedOut = () => {
  authState.value = false;
};

onMounted(() => {
  const path = window.location.pathname || '/main';
  if (!validPaths.has(path) && path !== '/') {
    setPath('/main', true);
  }
  if (path === '/') {
    setPath('/main', true);
  }

  const current = window.location.pathname || '/main';
  const search = window.location.search || '';
  if (protectedPaths.has(current) && !authState.value) {
    redirectToLogin(current, search, true);
  }

  window.addEventListener('popstate', handlePopState);
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
});
</script>

<template>
  <div class="min-h-screen bg-[#f3f7fc] text-[#1d3a5b]">
    <Header :current-path="normalizedPath" :is-authenticated="authState" @navigate="navigate" @logout="handleLogout" />

    <main>
      <MainPage
        v-if="normalizedPath === '/main'"
        :concerts="concerts"
        @open-concert="openConcert"
        @navigate="navigate"
      />
      <ConcertListPage
        v-else-if="normalizedPath === '/concerts'"
        :concerts="concerts"
        @open-concert="openConcert"
      />
      <LoginPage
        v-else-if="normalizedPath === '/login'"
        @navigate="navigate"
        @logged-in="handleLoggedIn"
      />
      <SignupPage
        v-else-if="normalizedPath === '/signup'"
        @navigate="navigate"
      />
      <MyPage
        v-else-if="normalizedPath === '/mypage'"
        @navigate="navigate"
        @logged-out="handleLoggedOut"
      />
      <ConcertDetail
        v-else-if="normalizedPath === '/concert/detail'"
        :poster-image="selectedConcert.heroImage"
        :title="selectedConcert.title"
        :subtitle="selectedConcert.subtitle"
        @booking-start="handleBookingStart"
      />
      <ConcertOpenPage
        v-else-if="normalizedPath === '/concert/open'"
        @booking-start="handleBookingStart"
      />
      <QueueScreen v-else-if="normalizedPath === '/concert/queue'" @queue-complete="handleQueueComplete" />
      <SeatSelection v-else-if="normalizedPath === '/concert/seat'" @complete="handleSeatComplete" />
      <PaymentScreen
        v-else-if="normalizedPath === '/concert/payment'"
        :booking-data="bookingData"
        @payment-complete="handlePaymentComplete"
      />
      <PaymentRedirectSuccess
        v-else-if="normalizedPath === '/payments/success'"
        @navigate="navigate"
      />
      <PaymentRedirectFail
        v-else-if="normalizedPath === '/payments/fail'"
        @navigate="navigate"
      />
      <BookingConfirmation
        v-else-if="normalizedPath === '/concert/confirm'"
        :booking-data="bookingData"
        @navigate="navigate"
      />
      <ServerError v-else-if="normalizedPath === '/error'" @retry="navigate('/main')" />
      <SoldOut v-else-if="normalizedPath === '/soldout'" @back="navigate('/main')" />
      <MainPage
        v-else
        :concerts="concerts"
        @open-concert="openConcert"
        @navigate="navigate"
      />
    </main>

    <footer class="mt-10 border-t border-[#d8e2ee] bg-[#0f2641] py-8 text-[#c5d4e5] md:mt-12">
      <div class="mx-auto max-w-[1280px] px-4 text-xs sm:px-6">
        <div class="mb-4 flex flex-wrap gap-x-4 gap-y-1 font-bold text-[#f0f6fd]">
          <span>회사소개</span>
          <span>이용약관</span>
          <span>개인정보처리방침</span>
          <span>티켓판매안내</span>
          <span>고객센터</span>
        </div>
        <p class="leading-relaxed">
          (주)FairLine Ticket | 대표이사: 김철수 | 사업자등록번호: 123-45-67890
          <br />
          주소: 서울특별시 강남구 테헤란로 123 | 통신판매업신고: 2026-서울강남-00000
          <br />
          고객센터: 1544-0000 (평일 09:00~18:00) | 이메일: help@flatticket.com
        </p>
        <p class="mt-4 text-[#94a8bd]">Copyright © FairLine Ticket Corp. All Rights Reserved.</p>
      </div>
    </footer>
  </div>
</template>
