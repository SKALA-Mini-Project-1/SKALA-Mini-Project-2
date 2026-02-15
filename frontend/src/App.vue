<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import Header from './components/Header.vue';
import ConcertDetail from './components/ConcertDetail.vue';
import QueueScreen from './components/QueueScreen.vue';
import SeatSelection from './components/SeatSelection.vue';
import PaymentScreen from './components/PaymentScreen.vue';
import BookingConfirmation from './components/BookingConfirmation.vue';
import MyPage from './components/MyPage.vue';
import ServerError from './components/ServerError.vue';
import SoldOut from './components/SoldOut.vue';
import LoginPage from './components/LoginPage.vue';
import SignupPage from './components/SignupPage.vue';
import type { BookingData, Seat } from './types';
import { apiRequest } from './services/api';
import { clearAuth, getToken, isLoggedIn } from './services/auth';

const bookingData = reactive<BookingData>({
  date: null,
  session: null,
  seats: []
});

const authState = ref(isLoggedIn());
const currentPath = ref(window.location.pathname || '/concert/detail');

const validPaths = new Set([
  '/concert/detail',
  '/concert/queue',
  '/concert/seat',
  '/concert/payment',
  '/concert/confirm',
  '/mypage',
  '/login',
  '/signup',
  '/error',
  '/soldout'
]);

const normalizedPath = computed(() => {
  if (currentPath.value === '/') {
    return '/concert/detail';
  }

  if (validPaths.has(currentPath.value)) {
    return currentPath.value;
  }

  return '/concert/detail';
});

const setPath = (path: string, replace = false) => {
  if (replace) {
    window.history.replaceState({}, '', path);
  } else {
    window.history.pushState({}, '', path);
  }
  currentPath.value = path;
};

const navigate = (path: string) => {
  setPath(path);
};

const handlePopState = () => {
  currentPath.value = window.location.pathname || '/concert/detail';
};

const handleBookingStart = (date: string, session: string) => {
  bookingData.date = date;
  bookingData.session = session;
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
    if (normalizedPath.value === '/mypage') {
      navigate('/login');
    }
  }
};

const handleLoggedIn = () => {
  authState.value = true;
};

const handleLoggedOut = () => {
  authState.value = false;
};

onMounted(() => {
  const path = window.location.pathname || '/concert/detail';
  if (!validPaths.has(path) && path !== '/') {
    setPath('/concert/detail', true);
  }
  if (path === '/') {
    setPath('/concert/detail', true);
  }
  window.addEventListener('popstate', handlePopState);
});

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState);
});
</script>

<template>
  <div class="min-h-screen bg-white font-sans text-[#333]">
    <Header :current-path="normalizedPath" :is-authenticated="authState" @navigate="navigate" @logout="handleLogout" />

    <main>
      <LoginPage
        v-if="normalizedPath === '/login'"
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
        @booking-start="handleBookingStart"
      />
      <QueueScreen v-else-if="normalizedPath === '/concert/queue'" @queue-complete="handleQueueComplete" />
      <SeatSelection v-else-if="normalizedPath === '/concert/seat'" @complete="handleSeatComplete" />
      <PaymentScreen
        v-else-if="normalizedPath === '/concert/payment'"
        :booking-data="bookingData"
        @payment-complete="handlePaymentComplete"
      />
      <BookingConfirmation
        v-else-if="normalizedPath === '/concert/confirm'"
        :booking-data="bookingData"
        @navigate="navigate"
      />
      <ServerError v-else-if="normalizedPath === '/error'" @retry="navigate('/concert/detail')" />
      <SoldOut v-else-if="normalizedPath === '/soldout'" @back="navigate('/concert/detail')" />
      <ConcertDetail v-else @booking-start="handleBookingStart" />
    </main>

    <footer class="mt-10 border-t border-[#e0e0e0] bg-[#f5f5f5] py-6 md:mt-12 md:py-8">
      <div class="mx-auto max-w-[1200px] px-3 text-xs text-[#999] sm:px-4">
        <div class="mb-4 flex flex-wrap gap-x-4 gap-y-1 font-bold text-[#666]">
          <span>회사소개</span>
          <span>이용약관</span>
          <span>개인정보처리방침</span>
          <span>청소년보호정책</span>
          <span>고객센터</span>
        </div>
        <p class="leading-relaxed">
          (주)FairLine Ticket | 대표이사: 김철수 | 사업자등록번호: 123-45-67890
          <br />
          주소: 서울특별시 강남구 테헤란로 123 | 통신판매업신고: 2025-서울강남-00000
          <br />
          고객센터: 1544-0000 (평일 09:00~18:00) | 팩스: 02-0000-0000 | 이메일: help@ticketkorea.com
        </p>
        <p class="mt-4 text-[#ccc]">Copyright © FairLine Ticket Corp. All Rights Reserved.</p>
      </div>
    </footer>
  </div>
</template>
