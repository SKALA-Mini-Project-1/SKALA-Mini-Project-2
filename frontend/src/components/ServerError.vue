<script setup lang="ts">
import { AlertTriangle, RefreshCw } from 'lucide-vue-next';

const emit = defineEmits<{
  retry: [];
}>();
</script>

<template>
  <div class="flex min-h-[600px] flex-col items-center justify-center bg-white p-4 text-center md:p-8">
    <div class="mb-6 flex h-24 w-24 items-center justify-center rounded-full bg-gray-100">
      <AlertTriangle :size="48" class="text-[#E8000B]" />
    </div>
    <h2 class="mb-2 text-xl font-bold text-[#333] md:text-2xl">서비스 접속이 원활하지 않습니다.</h2>
    <p class="mb-8 max-w-md text-sm text-[#666] md:text-base">
      현재 접속자가 많아 서비스 이용이 지연되고 있습니다.
      <br />
      잠시 후 다시 시도해 주시기 바랍니다.
      <br />
      (Error Code: 503)
    </p>
    <button
      class="flex items-center space-x-2 rounded-sm bg-[#333] px-8 py-3 font-bold text-white transition-colors hover:bg-black"
      @click="emit('retry')"
    >
      <RefreshCw :size="18" />
      <span>다시 시도하기</span>
    </button>
  </div>
</template>
