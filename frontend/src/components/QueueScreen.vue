<script setup lang="ts">
import { AlertTriangle, Clock, Info, Loader2 } from 'lucide-vue-next';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const emit = defineEmits<{
  queueComplete: [];
}>();

const props = defineProps<{
  concertId: string | null;
  scheduleId: string | null;
}>();

const position = ref(0);
const progress = ref(0);
const isSurge = ref(false);
const hasLeftQueue = ref(false);

let queueTimer: ReturnType<typeof setInterval> | null = null;

const concertIdValue = computed(() => {
  if (!props.concertId) {
    return null;
  }
  const parsed = Number(props.concertId);
  return Number.isNaN(parsed) ? null : parsed;
});

const canStartQueue = computed(() => concertIdValue.value !== null);
const scheduleIdValue = computed(() => {
  if (!props.scheduleId) {
    return null;
  }
  const parsed = Number(props.scheduleId);
  return Number.isNaN(parsed) ? null : parsed;
});

const buildQueueUrl = (path: 'start' | 'status' | 'leave') => {
  const params = new URLSearchParams();
  if (concertIdValue.value !== null) {
    params.set('concertId', String(concertIdValue.value));
  }
  if (scheduleIdValue.value !== null) {
    params.set('scheduleId', String(scheduleIdValue.value));
  }
  return `http://localhost:10010/api/ticketing/${path}?${params.toString()}`;
};

async function leaveQueue() {
  if (hasLeftQueue.value || !canStartQueue.value || scheduleIdValue.value === null) {
    return;
  }

  hasLeftQueue.value = true;
  const token = localStorage.getItem("ticketkorea_access_token");
  if (!token) {
    return;
  }

  try {
    await fetch(buildQueueUrl('leave'), {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`
      },
      keepalive: true
    });
  } catch (err) {
    console.error("대기열 이탈 처리 실패", err);
  }
}

// 1️⃣ 대기열 진입
async function startQueue() {
  if (!canStartQueue.value || scheduleIdValue.value === null) {
    console.error('대기열 진입 실패: concertId 또는 scheduleId가 없습니다.');
    return;
  }

  const token = localStorage.getItem("ticketkorea_access_token");

  console.log("START QUEUE CALLED");
  console.log("TOKEN:", token);
  console.log("CONCERT ID:", concertIdValue.value);
  console.log("SCHEDULE ID:", scheduleIdValue.value);

  const res = await fetch(
    buildQueueUrl('start'),
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`
      }
    }
  );

  console.log("START RESPONSE STATUS:", res.status);
}


async function checkStatus() {
  if (!canStartQueue.value || scheduleIdValue.value === null) {
    return;
  }

  try {
    const token = localStorage.getItem("ticketkorea_access_token");

    const res = await fetch(
      buildQueueUrl('status'),
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    );

    const data = await res.json();
    console.log("status data:", data);

    if (data.enter) {
      if (queueTimer) clearInterval(queueTimer);
      hasLeftQueue.value = true;

      const seatToken = localStorage.getItem("ticketkorea_access_token");

      const seatRes = await fetch(
        `http://localhost:8081/api/seats/seats?token=${encodeURIComponent(data.entryToken)}&concertId=${concertIdValue.value}&scheduleId=${scheduleIdValue.value}`,
        {
          headers: {
            Authorization: `Bearer ${seatToken}`
          }
        }
      );

      if (seatRes.ok) {
        emit("queueComplete");
      }
    } else {
      position.value = data.rank ?? 0;
    }

  } catch (err) {
    console.error("대기열 polling 실패", err);
  }
}



onMounted(async () => {
  if (!canStartQueue.value) {
    return;
  }
  hasLeftQueue.value = false;
  await startQueue(); // 🔥 반드시 먼저 진입
  queueTimer = setInterval(checkStatus, 1000);
  window.addEventListener('pagehide', leaveQueue);
});

onBeforeUnmount(() => {
  if (queueTimer) clearInterval(queueTimer);
  window.removeEventListener('pagehide', leaveQueue);
  void leaveQueue();
});

const waitLabel = computed(() =>
  `${Math.ceil(position.value / 100)}분 ${position.value % 60}초`
);

const aheadCount = computed(() =>
  Math.max(0, position.value - 1).toLocaleString()
);
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
