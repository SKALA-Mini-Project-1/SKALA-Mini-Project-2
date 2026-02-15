<script setup lang="ts">
import { Menu, Search, User } from 'lucide-vue-next';

const props = defineProps<{
  currentPath: string;
  isAuthenticated: boolean;
}>();

const emit = defineEmits<{
  navigate: [path: string];
  logout: [];
}>();

const navItems: Array<{ path: string; label: string }> = [
  { path: '/concert/detail', label: '공연상세' },
  { path: '/concert/queue', label: '대기열' },
  { path: '/concert/seat', label: '좌석선택' },
  { path: '/concert/payment', label: '결제하기' },
  { path: '/concert/confirm', label: '예매확인' },
  { path: '/mypage', label: '마이페이지' }
];

const isActive = (path: string) => props.currentPath.startsWith(path);
</script>

<template>
  <header class="sticky top-0 z-50 w-full border-b border-[#e0e0e0] bg-white">
    <div class="hidden border-b border-[#e0e0e0] bg-[#f8f8f8] py-1 md:block">
      <div class="mx-auto flex max-w-[1200px] justify-end space-x-4 px-4 text-xs text-[#666]">
        <button v-if="!isAuthenticated" class="hover:underline" @click="emit('navigate', '/login')">로그인</button>
        <button v-if="!isAuthenticated" class="hover:underline" @click="emit('navigate', '/signup')">회원가입</button>
        <button v-if="isAuthenticated" class="hover:underline" @click="emit('logout')">로그아웃</button>
        <span class="text-[#ddd]">|</span>
        <button class="hover:underline">고객센터</button>
        <span class="text-[#ddd]">|</span>
        <button class="hover:underline" @click="emit('navigate', '/mypage')">마이페이지</button>
      </div>
    </div>

    <div class="mx-auto flex max-w-[1200px] items-center justify-between px-4 py-3 md:h-20 md:py-0">
      <div class="flex items-center space-x-3 md:space-x-8">
        <button
          class="flex items-center text-xl font-bold tracking-tighter text-[#FF6B00] md:text-2xl"
          @click="emit('navigate', '/concert/detail')"
        >
          <span class="mr-1">🎫</span> FairLine Ticket
        </button>

        <div class="relative hidden md:block">
          <input
            type="text"
            placeholder="공연, 뮤지컬, 티켓 검색"
            class="h-10 w-80 border-2 border-[#FF6B00] pl-4 pr-10 text-sm outline-none"
          />
          <button class="absolute right-0 top-0 flex h-10 w-10 items-center justify-center text-[#FF6B00]">
            <Search :size="20" />
          </button>
        </div>
      </div>

      <div class="flex items-center space-x-3 md:space-x-4">
        <button
          class="hidden items-center space-x-1 rounded-sm border border-[#ddd] px-3 py-1.5 text-sm hover:bg-gray-50 md:flex"
          @click="emit('navigate', '/mypage')"
        >
          <User :size="16" />
          <span>나의 예매</span>
        </button>
        <button class="md:hidden">
          <Menu :size="24" />
        </button>
      </div>
    </div>

    <div class="border-t border-[#e0e0e0]">
      <div class="mx-auto max-w-[1200px] px-4">
        <nav class="flex space-x-1 overflow-x-auto whitespace-nowrap">
          <button
            v-for="item in navItems"
            :key="item.path"
            class="border-b-2 px-6 py-3 text-sm font-bold transition-colors"
            :class="
              isActive(item.path)
                ? 'border-[#FF6B00] text-[#FF6B00]'
                : 'border-transparent text-[#333] hover:text-[#FF6B00]'
            "
            @click="emit('navigate', item.path)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>
    </div>

    <div class="hidden border-b border-[#e0e0e0] bg-[#f5f5f5] py-2 md:block">
      <div class="mx-auto flex max-w-[1200px] items-center px-4 text-xs text-[#666]">
        <span>홈</span>
        <span class="mx-2 text-[#999]">&gt;</span>
        <span>콘서트</span>
        <span class="mx-2 text-[#999]">&gt;</span>
        <span class="font-bold text-[#333]">IU 2025 HEREH WORLD TOUR ENCORE</span>
      </div>
    </div>
  </header>
</template>
