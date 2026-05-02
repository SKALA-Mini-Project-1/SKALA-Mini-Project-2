<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { cancelPayment, sendPaymentExitSignal } from './data/payments'
import Header from './components/Header.vue'
import MainPage from './components/MainPage.vue'
import ConcertListPage from './components/ConcertListPage.vue'
import ConcertDetail from './components/ConcertDetail.vue'
import ConcertOpenPage from './components/ConcertOpenPage.vue'
import QueueScreen from './components/QueueScreen.vue'
import SeatSelection from './components/SeatSelection.vue'
import PaymentScreen from './components/PaymentScreen.vue'
import BookingConfirmation from './components/BookingConfirmation.vue'
import PaymentRedirectSuccess from './components/PaymentRedirectSuccess.vue'
import PaymentRedirectFail from './components/PaymentRedirectFail.vue'
import MyPage from './components/MyPage.vue'
import ConfirmModal from './components/ConfirmModal.vue'
import ServerError from './components/ServerError.vue'
import SoldOut from './components/SoldOut.vue'
import LoginPage from './components/LoginPage.vue'
import SignupPage from './components/SignupPage.vue'
import BookingGuidePage from './components/BookingGuidePage.vue'
import SupportPage from './components/SupportPage.vue'
import OpsIncidentListPage from './components/ops/OpsIncidentListPage.vue'
import OpsIncidentDetailPage from './components/ops/OpsIncidentDetailPage.vue'
import type { BookingData, Seat } from './types'
import { apiRequest } from './services/api'
import { clearAuth, getToken, isLoggedIn } from './services/auth'
import { fetchConcerts } from './services/concerts'
import { clearActivePaymentSession, getActivePaymentSession } from './services/paymentSession'
import type { ConcertItem } from './types'

const bookingData = reactive<BookingData>({
  bookingId: null,
  bookingNumber: null,
  concertId: null,
  scheduleId: null,
  concertTitle: null,
  concertVenue: null,
  date: null,
  session: null,
  seats: [],
})

const authState = ref(isLoggedIn())
const concerts = ref<ConcertItem[]>([])
const currentPath = ref(window.location.pathname || '/main')
const currentSearch = ref(window.location.search || '')
const pendingPath = ref<string | null>(null)
const pendingBooking = ref<{
  concertId: string
  concertTitle: string
  concertVenue: string
  date: string
  session: string
} | null>(null)

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
  '/guide',
  '/support',
  '/error',
  '/soldout',
  '/ops',
])

const protectedPaths = new Set([
  '/mypage',
  '/concert/queue',
  '/concert/seat',
  '/concert/payment',
  '/concert/confirm',
])

const normalizedPath = computed(() => {
  if (currentPath.value === '/') {
    return '/main'
  }

  if (validPaths.has(currentPath.value)) {
    return currentPath.value
  }

  return '/main'
})

const opsIncidentId = computed(() => {
  if (normalizedPath.value !== '/ops') return null
  const params = new URLSearchParams(currentSearch.value)
  return params.get('incident')
})

const selectedConcertId = computed(() => {
  const params = new URLSearchParams(currentSearch.value)
  return params.get('concert')
})

const selectedConcert = computed(() => {
  if (concerts.value.length === 0) {
    return null
  }
  if (!selectedConcertId.value) {
    return concerts.value[0]
  }
  return concerts.value.find((concert) => concert.id === selectedConcertId.value) ?? concerts.value[0]
})

const setPath = (path: string, replace = false) => {
  const url = new URL(path, window.location.origin)
  const next = `${url.pathname}${url.search}`

  if (replace) {
    window.history.replaceState({}, '', next)
  } else {
    window.history.pushState({}, '', next)
  }

  currentPath.value = url.pathname
  currentSearch.value = url.search
}

const redirectToLogin = (path: string, search = '', replace = false) => {
  pendingPath.value = `${path}${search}`
  setPath('/login', replace)
}

const navigate = (path: string) => {
  void cancelActivePaymentIfNeeded(path, 'ROUTE_LEAVE')
  const url = new URL(path, window.location.origin)

  if (protectedPaths.has(url.pathname) && !authState.value) {
    redirectToLogin(url.pathname, url.search)
    return
  }

  setPath(path)
}

const openConcert = (concertId: string) => {
  navigate(`/concert/detail?concert=${concertId}`)
}

const handlePopState = () => {
  const path = window.location.pathname || '/main'
  const search = window.location.search || ''

  void cancelActivePaymentIfNeeded(`${path}${search}`, 'ROUTE_LEAVE')

  if (protectedPaths.has(path) && !authState.value) {
    redirectToLogin(path, search, true)
    return
  }

  currentPath.value = path
  currentSearch.value = search
}

const handleBookingStart = (date: string, session: string) => {
  if (!selectedConcert.value) {
    return
  }

  const concertId = selectedConcert.value.id
  const concertTitle = `${selectedConcert.value.title} ${selectedConcert.value.subtitle}`
  const concertVenue = selectedConcert.value.venue

  if (!authState.value) {
    pendingBooking.value = { concertId, concertTitle, concertVenue, date, session }
    redirectToLogin('/concert/detail', `?concert=${concertId}`)
    return
  }

  bookingData.concertId = selectedConcert.value.id
  bookingData.scheduleId = session
  bookingData.concertTitle = concertTitle
  bookingData.concertVenue = concertVenue
  bookingData.date = date
  bookingData.session = session
  bookingData.bookingId = null
  bookingData.bookingNumber = null
  bookingData.seats = []
  navigate('/concert/queue')
}

const handleQueueComplete = () => {
  navigate('/concert/seat')
}

const handleSeatComplete = (payload: { seats: Seat[]; bookingId: string }) => {
  bookingData.seats = payload.seats
  bookingData.bookingId = payload.bookingId
  navigate('/concert/payment')
}

const showLogoutConfirm = ref(false)

const handleLogout = () => {
  showLogoutConfirm.value = true
}

const doLogout = async () => {
  showLogoutConfirm.value = false
  try {
    const token = getToken()
    await cancelActivePaymentIfNeeded('/login', 'LOGOUT')
    if (token) {
      await apiRequest<{ message: string; status: string }>('/api/users/logout', {
        method: 'POST',
        token,
      })
    }
  } catch {
    // 서버 로그아웃 실패여도 클라이언트 세션은 제거
  } finally {
    clearActivePaymentSession()
    clearAuth()
    authState.value = false
    if (protectedPaths.has(normalizedPath.value)) {
      navigate('/login')
    }
  }
}

const handleLoggedIn = () => {
  authState.value = true

  if (pendingBooking.value) {
    bookingData.concertId = pendingBooking.value.concertId
    bookingData.scheduleId = pendingBooking.value.session
    bookingData.concertTitle = pendingBooking.value.concertTitle
    bookingData.concertVenue = pendingBooking.value.concertVenue
    bookingData.date = pendingBooking.value.date
    bookingData.session = pendingBooking.value.session
    pendingBooking.value = null
    pendingPath.value = null
    navigate('/concert/queue')
    return
  }

  if (pendingPath.value) {
    const targetPath = pendingPath.value
    pendingPath.value = null
    navigate(targetPath)
    return
  }

  navigate('/mypage')
}

const handleLoggedOut = () => {
  clearActivePaymentSession()
  authState.value = false
}

const cancelActivePaymentIfNeeded = async (nextPath: string, reasonCode: string) => {
  const current = normalizedPath.value
  const nextUrl = new URL(nextPath, window.location.origin)
  if (current !== '/concert/payment' || nextUrl.pathname === '/concert/payment') {
    return
  }

  const session = getActivePaymentSession()
  const token = getToken()
  if (!session || !token) {
    return
  }

  const payload = {
    reasonCode,
    source: 'APP_NAVIGATION',
    clientRoute: nextUrl.pathname
  }

  try {
    await sendPaymentExitSignal(session.paymentId, token, payload)
  } catch {
    // 운영 추적 보강용 best-effort 호출
  }

  try {
    await cancelPayment(session.paymentId, token, payload)
    clearActivePaymentSession()
  } catch {
    // 실패 시 스케줄러가 EXPIRED로 정리한다.
  }
}

onMounted(() => {
  void fetchConcerts()
    .then((items) => {
      concerts.value = items
    })
    .catch(() => {
      concerts.value = []
    })

  const path = window.location.pathname || '/main'
  if (!validPaths.has(path) && path !== '/') {
    setPath('/main', true)
  }
  if (path === '/') {
    setPath('/main', true)
  }

  const current = window.location.pathname || '/main'
  const search = window.location.search || ''
  if (protectedPaths.has(current) && !authState.value) {
    redirectToLogin(current, search, true)
  }

  window.addEventListener('popstate', handlePopState)
})

onUnmounted(() => {
  window.removeEventListener('popstate', handlePopState)
})
</script>

<template>
  <!-- 운영자 대시보드: 라이트 레이아웃 -->
  <div v-if="normalizedPath === '/ops'" class="min-h-screen" style="background:radial-gradient(circle at 8% 0%, #f7fbff 0%, #edf4fc 35%, #f3f7fc 100%); color:#173451;">
    <!-- utility bar -->
    <div style="background:#f7fafd; border-bottom:1px solid #dfe7f0;">
      <div class="mx-auto max-w-[1280px] flex justify-end gap-4 px-6 py-2 text-xs" style="color:#4f6480;">
        <button class="hover:underline bg-transparent border-none cursor-pointer font-[inherit] text-xs" style="color:#4f6480;" @click="navigate('/main')">← 서비스로</button>
        <span style="color:#c8d5e2;">|</span>
        <span style="color:#ff7a00; font-weight:700;">운영 대시보드</span>
      </div>
    </div>
    <!-- logo row -->
    <header class="sticky top-0 z-50 border-b" style="border-color:#d8e2ef; background:rgba(255,255,255,.95); backdrop-filter:blur(6px);">
      <div class="mx-auto max-w-[1280px] flex items-center justify-between px-6 py-3">
        <div class="flex items-center gap-3">
          <img src="/fairline_ticket_favicon.jpg" alt="FairlineTicket" class="h-10 w-auto object-contain rounded-lg" />
          <span class="text-lg font-extrabold tracking-tight" style="color:#ff7a00;">FairlineTicket</span>
          <span style="color:#d8e2ef;">/</span>
          <span class="text-sm font-semibold" style="color:#4f6480;">결제 운영 대시보드</span>
        </div>
      </div>
      <!-- nav -->
      <div style="border-top:1px solid #e0e7f0;">
        <div class="mx-auto max-w-[1280px] px-6 flex gap-1">
          <button class="border-b-2 px-5 py-3 text-sm font-bold" style="border-color:transparent; color:#2c4764; background:none; cursor:pointer; font-family:inherit;" @click="navigate('/main')">메인</button>
          <button class="border-b-2 px-5 py-3 text-sm font-bold" style="border-color:transparent; color:#2c4764; background:none; cursor:pointer; font-family:inherit;" @click="navigate('/concerts')">콘서트</button>
          <button class="border-b-2 px-5 py-3 text-sm font-bold" style="border-color:#ff7a00; color:#ff7a00; background:none; cursor:pointer; font-family:inherit;">운영 대시보드</button>
        </div>
      </div>
    </header>
    <main class="mx-auto max-w-[1280px] px-6 py-8">
      <OpsIncidentDetailPage
        v-if="opsIncidentId"
        :incident-id="opsIncidentId"
        @back="navigate('/ops')"
      />
      <OpsIncidentListPage
        v-else
        @open-detail="(id) => navigate(`/ops?incident=${id}`)"
      />
    </main>
  </div>

  <div v-else class="min-h-screen bg-[#f3f7fc] text-[#1d3a5b]">
    <ConfirmModal
      v-if="showLogoutConfirm"
      message="로그아웃 하시겠습니까?"
      @confirm="doLogout"
      @cancel="showLogoutConfirm = false"
    />
    <Header
      :current-path="normalizedPath"
      :is-authenticated="authState"
      @navigate="navigate"
      @logout="handleLogout"
    />

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
      <SignupPage v-else-if="normalizedPath === '/signup'" @navigate="navigate" />
      <BookingGuidePage v-else-if="normalizedPath === '/guide'" />
      <SupportPage v-else-if="normalizedPath === '/support'" />
      <MyPage
        v-else-if="normalizedPath === '/mypage'"
        @navigate="navigate"
        @logged-out="handleLoggedOut"
      />
      <ConcertDetail
        v-else-if="normalizedPath === '/concert/detail' && selectedConcert"
        :poster-image="selectedConcert.heroImage"
        :title="selectedConcert.title"
        :subtitle="selectedConcert.subtitle"
        :period="selectedConcert.period"
        :venue="selectedConcert.venue"
        :runtime="selectedConcert.runtime"
        :age-rating="selectedConcert.ageRating"
        :available-dates="selectedConcert.availableDates"
        :sessions="selectedConcert.sessions"
        @booking-start="handleBookingStart"
      />
      <ConcertOpenPage
        v-else-if="normalizedPath === '/concert/open'"
        @booking-start="handleBookingStart"
      />
      <QueueScreen
        v-else-if="normalizedPath === '/concert/queue'"
        :concert-id="bookingData.concertId"
        :schedule-id="bookingData.scheduleId"
        @queue-complete="handleQueueComplete"
      />
      <SeatSelection
        v-else-if="normalizedPath === '/concert/seat'"
        :concert-id="bookingData.concertId"
        :schedule-id="bookingData.scheduleId"
        @complete="handleSeatComplete"
      />
      <PaymentScreen
        v-else-if="normalizedPath === '/concert/payment'"
        :booking-data="bookingData"
        @navigate="navigate"
      />
      <PaymentRedirectSuccess
        v-else-if="normalizedPath === '/payments/success'"
        @navigate="navigate"
      />
      <PaymentRedirectFail v-else-if="normalizedPath === '/payments/fail'" @navigate="navigate" />
      <BookingConfirmation
        v-else-if="normalizedPath === '/concert/confirm'"
        :booking-data="bookingData"
        @navigate="navigate"
      />
      <ServerError v-else-if="normalizedPath === '/error'" @retry="navigate('/main')" />
      <SoldOut v-else-if="normalizedPath === '/soldout'" @back="navigate('/main')" />
      <MainPage v-else :concerts="concerts" @open-concert="openConcert" @navigate="navigate" />
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
          Fairline Ticket | 대표이사: 등차수열 | 사업자등록번호: 123-45-67890
          <br />
          주소: 경기 성남시 분당구 성남대로 343번길 9, 8층 SKALA | 통신판매업신고:
          2026-성남분당-00000
          <br />
          고객센터: 1544-0000 (평일 09:00~18:00) | 이메일: fairlineTicket@gmail.com
        </p>
        <p class="mt-4 text-[#94a8bd]">Copyright © Fairline Ticket Corp. All Rights Reserved.</p>
      </div>
    </footer>
  </div><!-- /v-else 서비스 레이아웃 -->
</template>
