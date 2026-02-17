<script setup lang="ts">
import { AlertCircle, Clock3 } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const emit = defineEmits<{
  bookingStart: [date: string, session: string];
}>();

const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
const isDemoMode = new URLSearchParams(window.location.search).get('demo') === '1';
const demoOpenAt = ref<number | null>(null);

if (isDemoMode) {
  demoOpenAt.value = Date.now() + 1 * 60 * 1000;
}

const openAt = computed(() => {
  if (isDemoMode && demoOpenAt.value) {
    return new Date(demoOpenAt.value);
  }

  const base = new Date(now.value);
  const target = new Date(base);
  target.setHours(20, 0, 0, 0);

  if (base.getTime() > target.getTime()) {
    target.setDate(target.getDate() + 1);
  }

  return target;
});

const remainingMs = computed(() => Math.max(0, openAt.value.getTime() - now.value));
const isOpen = computed(() => remainingMs.value <= 0);
const isLastMinute = computed(() => !isOpen.value && remainingMs.value <= 30_000);

const formattedOpenAt = computed(() => {
  const d = openAt.value;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  const sec = String(d.getSeconds()).padStart(2, '0');
  return `${y}.${m}.${day} ${h}:${min}:${sec}`;
});

const countdownLabel = computed(() => {
  const totalSeconds = Math.ceil(remainingMs.value / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
});

const bookingDate = computed(() => {
  const d = openAt.value;
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
});

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer);
  }
});
</script>

<template>
  <div class="mx-auto max-w-[1000px] p-3 sm:p-4 md:p-8">
    <div class="overflow-hidden rounded-2xl border border-[#dbe7f4] bg-white shadow-sm">
      <div class="bg-[#102946] px-5 py-6 text-white md:px-7 md:py-8">
        <p class="mb-2 text-xs font-semibold text-[#9ec2eb]">오픈 대기 페이지</p>
        <h2 class="text-2xl font-black md:text-3xl">FairLine Ticket 선착순 예매</h2>
        <p class="mt-2 text-sm text-[#c2d8ef]">정각 전에는 예매 버튼이 비활성화되며, 오픈 시점에 자동 활성화됩니다.</p>
        <p v-if="isDemoMode" class="mt-3 inline-block rounded-full bg-amber-300/20 px-3 py-1 text-xs font-bold text-amber-200">
          DEMO MODE (지금부터 1분 카운트다운)
        </p>
      </div>

      <div class="space-y-6 p-5 md:p-7">
        <div class="rounded-xl border border-[#e4edf7] bg-[#f8fbff] p-4">
          <p class="text-sm font-bold text-[#214367]">오픈 예정 시각</p>
          <p class="mt-1 text-lg font-extrabold text-[#102946]">{{ formattedOpenAt }}</p>
          <p class="mt-2 text-xs text-[#6f87a2]">
            {{ isDemoMode ? '데모 모드: 페이지 진입 시점 + 1분' : '매일 오후 8시(20:00) 기준으로 동작합니다.' }}
          </p>
        </div>

        <div
          class="rounded-xl border p-5 text-center"
          :class="
            isOpen
              ? 'border-emerald-200 bg-emerald-50'
              : isLastMinute
                ? 'border-red-200 bg-red-50'
                : 'border-[#e6edf6] bg-white'
          "
        >
          <div class="mb-2 flex items-center justify-center gap-2 text-sm font-semibold text-[#516a86]">
            <Clock3 :size="16" />
            <span>티켓 오픈까지 남은 시간</span>
          </div>
          <p
            class="font-mono text-4xl font-black md:text-5xl"
            :class="isOpen ? 'text-emerald-600' : isLastMinute ? 'animate-pulse text-red-600' : 'text-[#122f4c]'"
          >
            {{ isOpen ? '00:00:00' : countdownLabel }}
          </p>
          <p v-if="isLastMinute" class="mt-2 text-sm font-bold text-red-600">30초 이내 오픈 예정</p>
          <p v-else-if="isOpen" class="mt-2 text-sm font-bold text-emerald-600">예매가 시작되었습니다.</p>
        </div>

        <div class="rounded-xl border border-[#ffe5c8] bg-[#fff7ef] p-4 text-sm text-[#8e663c]">
          <p class="mb-1 flex items-center gap-2 font-bold"><AlertCircle :size="16" />시연 안내</p>
          <p>
            정각 전에는 버튼이 비활성화 상태이며, 정각이 되면 즉시 활성화됩니다.
            로그인하지 않은 경우 클릭 시 로그인 페이지로 이동합니다.
          </p>
        </div>

        <button
          class="w-full rounded-xl py-4 text-base font-bold transition md:text-lg"
          :class="
            isOpen
              ? 'bg-[#ff7a00] text-white hover:bg-[#e86f00]'
              : 'cursor-not-allowed bg-[#dce6f0] text-[#86a0b8]'
          "
          :disabled="!isOpen"
          @click="emit('bookingStart', bookingDate, '1')"
        >
          {{ isOpen ? '예매하기' : '오픈 시간 전입니다' }}
        </button>
      </div>
    </div>
  </div>
</template>
