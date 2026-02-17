<script setup lang="ts">
import { Menu, Search, User } from 'lucide-vue-next';
import fairlineLogo from '../assets/fairline_ticket_logo.png';

const props = defineProps<{
  currentPath: string;
  isAuthenticated: boolean;
}>();

const emit = defineEmits<{
  navigate: [path: string];
  logout: [];
}>();

const navItems: Array<{ path: string; label: string }> = [
  { path: '/main', label: '메인' },
  { path: '/concerts', label: '콘서트' },
  { path: '/concert/detail', label: '상세' },
  { path: '/concert/open', label: '오픈대기' },
  { path: '/mypage', label: '마이페이지' }
];

const isActive = (path: string) => {
  if (path === '/main') {
    return props.currentPath === '/main';
  }
  return props.currentPath.startsWith(path);
};
</script>

<template>
  <header class="sticky top-0 z-50 w-full border-b border-[#d8e2ef] bg-white/95 backdrop-blur">
    <div class="hidden border-b border-[#dfe7f0] bg-[#f7fafd] py-2 md:block">
      <div class="mx-auto flex max-w-[1280px] justify-end space-x-4 px-4 text-xs text-[#4f6480]">
        <button v-if="!isAuthenticated" class="hover:underline" @click="emit('navigate', '/login')">로그인</button>
        <button v-if="!isAuthenticated" class="hover:underline" @click="emit('navigate', '/signup')">회원가입</button>
        <button v-if="isAuthenticated" class="hover:underline" @click="emit('logout')">로그아웃</button>
        <span class="text-[#c8d5e2]">|</span>
        <button class="hover:underline" @click="emit('navigate', '/support')">고객센터</button>
        <span class="text-[#c8d5e2]">|</span>
        <button class="hover:underline" @click="emit('navigate', '/mypage')">마이페이지</button>
      </div>
    </div>

    <div class="mx-auto flex max-w-[1280px] items-center justify-between px-4 py-3 md:h-28 md:py-0">
      <div class="flex items-center space-x-3 md:space-x-8">
        <button
          class="flex items-center"
          @click="emit('navigate', '/main')"
        >
          <img
            :src="fairlineLogo"
            alt="Fairline Ticket"
            class="h-14 w-auto object-contain md:h-20"
          />
        </button>

        <div class="relative hidden md:block">
          <input
            type="text"
            placeholder="아티스트, 공연명, 장소 검색"
            class="h-11 w-96 rounded-lg border border-[#d6e3f2] bg-[#f9fbfe] pl-4 pr-10 text-sm text-[#254563] outline-none focus:border-[#ff7a00]"
          />
          <button class="absolute right-0 top-0 flex h-11 w-10 items-center justify-center text-[#ff7a00]">
            <Search :size="20" />
          </button>
        </div>
      </div>

      <div class="flex items-center space-x-3 md:space-x-4">
        <button
          class="hidden items-center space-x-1 rounded-lg border border-[#d0deec] px-3 py-2 text-sm text-[#244260] hover:bg-[#f3f8fe] md:flex"
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

    <div class="border-t border-[#e0e7f0]">
      <div class="mx-auto max-w-[1280px] px-4">
        <nav class="flex space-x-1 overflow-x-auto whitespace-nowrap">
          <button
            v-for="item in navItems"
            :key="item.path"
            class="border-b-2 px-6 py-3 text-sm font-bold transition-colors"
            :class="
              isActive(item.path)
                ? 'border-[#ff7a00] text-[#ff7a00]'
                : 'border-transparent text-[#2c4764] hover:text-[#ff7a00]'
            "
            @click="emit('navigate', item.path)"
          >
            {{ item.label }}
          </button>
          <button class="flex items-center px-4 text-xs font-semibold text-[#6a819a] hover:text-[#ff7a00]" @click="emit('navigate', '/guide')">
            예매가이드
          </button>
          <button class="flex items-center px-4 text-xs font-semibold text-[#6a819a] hover:text-[#ff7a00]" @click="emit('navigate', '/support')">
            고객센터
          </button>
        </nav>
      </div>
    </div>
  </header>
</template>
