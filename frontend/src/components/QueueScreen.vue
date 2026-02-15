<script setup lang="ts">
import { AlertTriangle, Clock, Info, Loader2 } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const emit = defineEmits<{
  queueComplete: [];
}>();

const position = ref(15847);
const progress = ref(0);
const isSurge = ref(false);
let queueTimer: ReturnType<typeof setInterval> | null = null;
let surgeTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(() => {
  queueTimer = setInterval(() => {
    const next = Math.max(0, position.value - Math.floor(Math.random() * 50));
    position.value = next;
    progress.value = Math.min(100, progress.value + 0.5);
    if (next === 0) {
      if (queueTimer) {
        clearInterval(queueTimer);
        queueTimer = null;
      }
      setTimeout(() => emit('queueComplete'), 1000);
    }
  }, 1000);

  surgeTimer = setTimeout(() => {
    isSurge.value = true;
  }, 5000);
});

onBeforeUnmount(() => {
  if (queueTimer) {
    clearInterval(queueTimer);
  }
  if (surgeTimer) {
    clearTimeout(surgeTimer);
  }
});

const waitLabel = computed(() => `${Math.ceil(position.value / 100)}분 ${position.value % 60}초`);
const aheadCount = computed(() => Math.max(0, position.value - 1200).toLocaleString());
</script>

<template>
  <div class="flex min-h-[600px] items-center justify-center bg-gray-50 p-3 sm:p-4">
    <div class="w-full max-w-md overflow-hidden rounded-sm border border-[#e0e0e0] bg-white shadow-xl">
      <div class="p-4 text-center font-bold text-white" :class="isSurge ? 'bg-[#E8000B]' : 'bg-[#333]'">
        {{ isSurge ? '⚠️ 서버 접속 지연 (과부하)' : '서비스 접속 대기 중' }}
      </div>

      <div class="p-5 text-center sm:p-8">
        <div class="mb-6 sm:mb-8">
          <h2 class="mb-2 text-sm text-[#666]">현재 대기번호</h2>
          <div class="tabular-nums text-4xl font-bold tracking-tight text-[#FF6B00] sm:text-5xl">{{ position.toLocaleString() }}</div>
          <p class="mt-2 text-xs text-[#999]">내 앞 대기인원: {{ aheadCount }}명</p>
        </div>

        <div class="mb-6 sm:mb-8">
          <div class="mb-2 flex justify-between text-xs font-medium text-[#666]">
            <span>예상 대기시간</span>
            <span class="flex items-center text-[#FF6B00]"><Clock :size="12" class="mr-1" />{{ waitLabel }}</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-[#f0f0f0]">
            <div class="h-full bg-[#FF6B00] transition-[width] duration-500" :style="{ width: `${progress}%` }"></div>
          </div>
        </div>

        <div v-if="isSurge" class="mb-6 flex items-start rounded-sm border border-red-100 bg-red-50 p-3 text-left">
          <AlertTriangle :size="16" class="mr-2 mt-0.5 shrink-0 text-[#E8000B]" />
          <p class="text-xs leading-relaxed text-red-700">
            현재 접속자가 많아 대기시간이 길어질 수 있습니다.
            <br />
            잠시만 기다려주시면 자동으로 연결됩니다.
          </p>
        </div>

        <div class="flex items-start rounded-sm border border-[#eee] bg-[#f8f8f8] p-4 text-left text-xs text-[#666]">
          <Info :size="16" class="mr-2 mt-0.5 shrink-0 text-[#999]" />
          <p>
            새로고침을 하거나 재접속하시면 대기순서가 초기화되어
            <br />
            대기시간이 더 길어질 수 있습니다.
          </p>
        </div>
      </div>

      <div class="flex items-center justify-between border-t border-[#e0e0e0] bg-[#f5f5f5] p-3 text-xs">
        <div class="flex items-center text-[#666]"><Loader2 :size="12" class="mr-2 animate-spin" />자동 연결 중...</div>
        <div class="font-bold" :class="isSurge ? 'text-[#E8000B]' : 'text-green-600'">{{ isSurge ? '서버 혼잡' : '서버 상태 양호' }}</div>
      </div>
    </div>
  </div>
</template>
