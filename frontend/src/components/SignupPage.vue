<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { apiRequest, ApiError } from '../services/api'

interface EmailVerificationResponse {
  email: string
  message: string
  expirationMinutes: number | null
  resendAvailableAt: number | null
}

interface SignUpResponse {
  userId: number
  email: string
  name: string
  phone: string | null
  createdAt: string
  message: string
}

const emit = defineEmits<{
  navigate: [path: string]
}>()

const email = ref('')
const code = ref('')
const password = ref('')
const passwordConfirm = ref('')
const name = ref('')
const phone = ref('')

const emailSent = ref(false)
const emailVerified = ref(false)
const codeExpiresInSeconds = ref(0)
const resendCooldownSeconds = ref(0)
const loading = ref(false)
const infoMessage = ref('')
const errorMessage = ref('')

let countdownTimer: ReturnType<typeof setInterval> | null = null
let resendTimer: ReturnType<typeof setInterval> | null = null

const passwordMismatch = computed(
  () => passwordConfirm.value.length > 0 && password.value !== passwordConfirm.value,
)

const codeTimerText = computed(() => {
  const minutes = Math.floor(codeExpiresInSeconds.value / 60)
  const seconds = codeExpiresInSeconds.value % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const resendTimerText = computed(() => `${resendCooldownSeconds.value}초`)

const isCodeExpired = computed(
  () => emailSent.value && !emailVerified.value && codeExpiresInSeconds.value <= 0,
)

const canSubmitSignup = computed(
  () =>
    emailVerified.value &&
    password.value.length >= 8 &&
    passwordConfirm.value.length > 0 &&
    !passwordMismatch.value &&
    name.value.length > 0,
)

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

const stopResendCooldown = () => {
  if (resendTimer) {
    clearInterval(resendTimer)
    resendTimer = null
  }
}

const startResendCooldown = (seconds: number) => {
  resendCooldownSeconds.value = Math.max(0, seconds)
  stopResendCooldown()

  if (resendCooldownSeconds.value <= 0) {
    return
  }

  resendTimer = setInterval(() => {
    if (resendCooldownSeconds.value <= 1) {
      resendCooldownSeconds.value = 0
      stopResendCooldown()
      return
    }
    resendCooldownSeconds.value -= 1
  }, 1000)
}

const parseRetryAfterSeconds = (message: string): number | null => {
  const matched = message.match(/(\d+)\s*초/)
  if (!matched) {
    return null
  }
  const value = Number(matched[1])
  return Number.isNaN(value) ? null : value
}

const getSecondsFromUnixTimestamp = (unixTimestamp: number): number => {
  const nowInSeconds = Math.floor(Date.now() / 1000)
  return Math.max(0, unixTimestamp - nowInSeconds)
}

const startCountdown = (expirationMinutes: number | null) => {
  const duration = expirationMinutes ?? 5
  codeExpiresInSeconds.value = duration * 60
  stopCountdown()

  countdownTimer = setInterval(() => {
    if (codeExpiresInSeconds.value <= 1) {
      codeExpiresInSeconds.value = 0
      stopCountdown()
      return
    }
    codeExpiresInSeconds.value -= 1
  }, 1000)
}

const withLoading = async (callback: () => Promise<void>) => {
  if (loading.value) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    await callback()
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다.'
  } finally {
    loading.value = false
  }
}

const sendCode = async () => {
  if (resendCooldownSeconds.value > 0) {
    errorMessage.value = `인증 코드는 1분에 한 번만 발송할 수 있습니다. ${resendCooldownSeconds.value}초 후 재시도해주세요.`
    return
  }

  if (loading.value) {
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await apiRequest<EmailVerificationResponse>('/api/users/email/send', {
      method: 'POST',
      body: JSON.stringify({ email: email.value }),
    })

    emailSent.value = true
    emailVerified.value = false
    code.value = ''
    startCountdown(response.expirationMinutes)
    startResendCooldown(
      response.resendAvailableAt ? getSecondsFromUnixTimestamp(response.resendAvailableAt) : 60,
    )
    infoMessage.value = response.message
  } catch (error) {
    if (error instanceof ApiError) {
      const seconds = parseRetryAfterSeconds(error.message)
      if (seconds && seconds > 0) {
        startResendCooldown(seconds)
        errorMessage.value = `인증 코드는 1분에 한 번만 발송할 수 있습니다. ${seconds}초 후 재시도해주세요.`
      } else {
        errorMessage.value = error.message
      }
    } else {
      errorMessage.value = '요청 처리 중 오류가 발생했습니다.'
    }
  } finally {
    loading.value = false
  }
}

const verifyCode = async () => {
  if (isCodeExpired.value) {
    errorMessage.value = '인증 코드 유효시간이 만료되었습니다. 인증코드를 다시 발송해주세요.'
    return
  }

  await withLoading(async () => {
    const response = await apiRequest<EmailVerificationResponse>('/api/users/email/verify', {
      method: 'POST',
      body: JSON.stringify({ email: email.value, code: code.value }),
    })

    emailVerified.value = true
    stopCountdown()
    infoMessage.value = response.message
  })
}

const signup = async () => {
  if (!canSubmitSignup.value) {
    return
  }

  await withLoading(async () => {
    const response = await apiRequest<SignUpResponse>('/api/users/signup', {
      method: 'POST',
      body: JSON.stringify({
        email: email.value,
        password: password.value,
        name: name.value,
        phone: phone.value || null,
      }),
    })

    infoMessage.value = response.message
    emit('navigate', '/login')
  })
}

onUnmounted(() => {
  stopCountdown()
  stopResendCooldown()
})
</script>

<template>
  <div class="mx-auto max-w-[520px] px-4 py-10 md:py-14">
    <h2 class="mb-6 border-b-2 border-[#333] pb-2 text-2xl font-bold text-[#333]">회원가입</h2>

    <form class="space-y-4" @submit.prevent="signup">
      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">이메일</label>
        <div class="flex gap-2">
          <input
            v-model="email"
            type="email"
            required
            autocomplete="email"
            class="h-11 flex-1 rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
            placeholder="user@example.com"
          />
          <button
            type="button"
            :disabled="loading || resendCooldownSeconds > 0"
            class="h-11 min-w-[110px] rounded-sm border border-[#FF6B00] px-3 text-sm font-bold text-[#FF6B00] disabled:opacity-60"
            @click="sendCode"
          >
            {{ resendCooldownSeconds > 0 ? `재발송 ${resendTimerText}` : '인증코드 발송' }}
          </button>
        </div>
        <p v-if="resendCooldownSeconds > 0" class="mt-1 text-xs font-semibold text-[#666]">
          인증 코드는 1분에 한 번만 발송할 수 있습니다. {{ resendTimerText }} 후 재시도해주세요.
        </p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">인증코드</label>
        <div class="flex gap-2">
          <input
            v-model="code"
            type="text"
            maxlength="6"
            :disabled="!emailSent"
            class="h-11 flex-1 rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00] disabled:bg-[#f5f5f5]"
            placeholder="6자리 숫자"
          />
          <button
            type="button"
            :disabled="loading || !emailSent"
            class="h-11 min-w-[110px] rounded-sm border border-[#333] px-3 text-sm font-bold text-[#333] disabled:opacity-60"
            @click="verifyCode"
          >
            인증 확인
          </button>
        </div>
        <p
          v-if="emailSent && !emailVerified && !isCodeExpired"
          class="mt-1 text-xs font-semibold text-[#666]"
        >
          인증코드 유효시간 {{ codeTimerText }}
        </p>
        <p v-if="isCodeExpired" class="mt-1 text-xs font-semibold text-red-500">
          인증코드 유효시간이 만료되었습니다. 다시 발송해주세요.
        </p>
        <p v-if="emailVerified" class="mt-1 text-xs font-semibold text-green-600">
          이메일 인증이 완료되었습니다.
        </p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">비밀번호</label>
        <input
          v-model="password"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="8자 이상의 비밀번호를 입력하세요"
        />
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">비밀번호 확인</label>
        <input
          v-model="passwordConfirm"
          type="password"
          required
          minlength="8"
          autocomplete="new-password"
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="비밀번호를 다시 입력하세요"
        />
        <p v-if="passwordMismatch" class="mt-1 text-xs font-semibold text-red-500">
          비밀번호가 일치하지 않습니다.
        </p>
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">이름</label>
        <input
          v-model="name"
          type="text"
          required
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="이름을 입력하세요"
        />
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">전화번호 (선택)</label>
        <input
          v-model="phone"
          type="text"
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="010-1234-5678"
        />
      </div>

      <p v-if="infoMessage" class="text-sm font-semibold text-green-600">{{ infoMessage }}</p>
      <p v-if="errorMessage" class="text-sm font-semibold text-red-500">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="loading || !canSubmitSignup"
        class="h-11 w-full rounded-sm bg-[#FF6B00] text-sm font-bold text-white disabled:opacity-60"
      >
        {{ loading ? '처리 중...' : '회원가입 완료' }}
      </button>

      <button
        type="button"
        class="h-11 w-full rounded-sm border border-[#ddd] text-sm font-bold text-[#666]"
        @click="emit('navigate', '/login')"
      >
        로그인으로 이동
      </button>
    </form>
  </div>
</template>
