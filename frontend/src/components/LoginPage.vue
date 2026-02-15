<script setup lang="ts">
import { ref } from 'vue';
import { apiRequest, ApiError } from '../services/api';
import { setAuthUser, setToken } from '../services/auth';

interface LoginResponse {
  userId: number;
  email: string;
  name: string;
  message: string;
  accessToken: string;
}

const emit = defineEmits<{
  navigate: [path: string];
  loggedIn: [];
}>();

const email = ref('');
const password = ref('');
const loading = ref(false);
const errorMessage = ref('');

const submit = async () => {
  if (loading.value) {
    return;
  }

  errorMessage.value = '';
  loading.value = true;

  try {
    const response = await apiRequest<LoginResponse>('/api/users/login', {
      method: 'POST',
      body: JSON.stringify({
        email: email.value,
        password: password.value
      })
    });

    setToken(response.accessToken);
    setAuthUser({
      userId: response.userId,
      email: response.email,
      name: response.name
    });

    emit('loggedIn');
    emit('navigate', '/mypage');
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '로그인 중 오류가 발생했습니다.';
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="mx-auto max-w-[460px] px-4 py-10 md:py-14">
    <h2 class="mb-6 border-b-2 border-[#333] pb-2 text-2xl font-bold text-[#333]">로그인</h2>

    <form class="space-y-4" @submit.prevent="submit">
      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">이메일</label>
        <input
          v-model="email"
          type="email"
          required
          autocomplete="email"
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="user@example.com"
        />
      </div>

      <div>
        <label class="mb-1 block text-sm font-bold text-[#444]">비밀번호</label>
        <input
          v-model="password"
          type="password"
          required
          autocomplete="current-password"
          class="h-11 w-full rounded-sm border border-[#ddd] px-3 outline-none focus:border-[#FF6B00]"
          placeholder="비밀번호를 입력하세요"
        />
      </div>

      <p v-if="errorMessage" class="text-sm font-semibold text-red-500">{{ errorMessage }}</p>

      <button
        type="submit"
        :disabled="loading"
        class="h-11 w-full rounded-sm bg-[#FF6B00] text-sm font-bold text-white disabled:opacity-60"
      >
        {{ loading ? '로그인 중...' : '로그인' }}
      </button>

      <button
        type="button"
        class="h-11 w-full rounded-sm border border-[#ddd] text-sm font-bold text-[#666]"
        @click="emit('navigate', '/signup')"
      >
        회원가입
      </button>
    </form>
  </div>
</template>
