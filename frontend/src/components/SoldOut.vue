<script setup lang="ts">
import { ArrowLeft, Ticket } from 'lucide-vue-next';

const emit = defineEmits<{
  back: [];
}>();
</script>

<template>
  <div class="flex min-h-[600px] flex-col items-center justify-center bg-white p-4 text-center md:p-8">
    <div class="mb-6 flex h-24 w-24 items-center justify-center rounded-full bg-gray-100">
      <Ticket :size="48" class="text-[#999]" />
    </div>
    <h2 class="mb-2 text-xl font-bold text-[#333] md:text-2xl">전석 매진되었습니다.</h2>
    <p class="mb-8 max-w-md text-sm text-[#666] md:text-base">
      고객님의 성원에 감사드립니다.
      <br />
      취소표 발생 시 예매가 가능할 수 있습니다.
    </p>
    <div class="grid w-full max-w-sm grid-cols-1 gap-3 sm:max-w-none sm:grid-cols-2 sm:gap-4">
      <button
        class="flex items-center justify-center space-x-2 rounded-sm border border-[#ddd] px-6 py-3 font-bold text-[#333] transition-colors hover:bg-gray-50"
        @click="emit('back')"
      >
        <ArrowLeft :size="18" />
        <span>목록으로</span>
      </button>
      <button class="rounded-sm bg-[#FF6B00] px-6 py-3 font-bold text-white transition-colors hover:bg-[#e56000]">취소표 알림 신청</button>
    </div>
  </div>
</template>
