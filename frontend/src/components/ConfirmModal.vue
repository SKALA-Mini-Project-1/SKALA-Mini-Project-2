<script setup lang="ts">
defineProps<{
  message: string
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()
</script>

<template>
  <div class="fixed inset-0 z-[9999] flex items-center justify-center">
    <div class="absolute inset-0 bg-black/40" @click="emit('cancel')" />
    <div class="relative w-80 rounded-2xl bg-white px-8 py-7 shadow-2xl">
      <p class="mb-6 text-center text-base font-semibold text-[#1d3a5b]">{{ message }}</p>
      <div class="flex gap-3">
        <button
          class="h-11 flex-1 rounded-lg border border-[#d6e2ef] bg-white text-sm font-bold text-[#4e6782] hover:bg-[#f4f8fc]"
          @click="emit('cancel')"
        >
          취소
        </button>
        <button
          class="h-11 flex-1 rounded-lg bg-[#FF6B00] text-sm font-bold text-white hover:bg-[#e66000]"
          @click="emit('confirm')"
        >
          확인
        </button>
      </div>
    </div>
  </div>
</template>
