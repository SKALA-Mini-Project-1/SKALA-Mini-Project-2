<script setup lang="ts">
import { CalendarDays, ChevronRight, MapPin, Ticket } from 'lucide-vue-next';
import type { ConcertItem } from '../types';

const props = defineProps<{
  concerts: ConcertItem[];
}>();

const emit = defineEmits<{
  openConcert: [concertId: string];
  navigate: [path: string];
}>();

const heroConcert = props.concerts[0];
const hotConcerts = props.concerts.slice(1, 4);
</script>

<template>
  <div class="mx-auto max-w-[1280px] px-4 pb-12 pt-6 md:px-6 md:pb-16 md:pt-8">
    <section
      class="relative overflow-hidden rounded-3xl bg-[#081326] p-6 text-white md:p-10"
      :style="{
        backgroundImage: `linear-gradient(120deg, rgba(8,19,38,0.96), rgba(11,38,63,0.7)), url(${heroConcert.heroImage})`,
        backgroundSize: 'cover',
        backgroundPosition: 'center'
      }"
    >
      <div class="absolute -right-8 -top-8 h-40 w-40 rounded-full bg-[#ff7a00]/20 blur-2xl"></div>
      <div class="relative z-10 max-w-2xl">
        <p class="mb-3 inline-flex rounded-full bg-white/15 px-3 py-1 text-xs font-semibold tracking-wide">티켓 오픈 임박</p>
        <h1 class="text-3xl font-extrabold leading-tight md:text-5xl">{{ heroConcert.title }}</h1>
        <p class="mt-2 text-lg font-semibold text-[#ffbe7a]">{{ heroConcert.subtitle }}</p>
        <div class="mt-5 space-y-2 text-sm text-slate-200 md:text-base">
          <div class="flex items-center gap-2"><MapPin :size="16" />{{ heroConcert.venue }}</div>
          <div class="flex items-center gap-2"><CalendarDays :size="16" />{{ heroConcert.period }}</div>
        </div>
        <div class="mt-8 flex flex-wrap gap-3">
          <button class="rounded-xl bg-[#ff7a00] px-5 py-3 text-sm font-bold hover:bg-[#e86f00]" @click="emit('openConcert', heroConcert.id)">
            예매하기
          </button>
          <button class="rounded-xl border border-white/40 px-5 py-3 text-sm font-semibold hover:bg-white/10" @click="emit('navigate', '/concerts')">
            전체 공연 보기
          </button>
        </div>
      </div>
    </section>

    <section class="mt-8">
      <div class="mb-4 flex items-center justify-between">
        <h2 class="text-xl font-extrabold text-[#10223a] md:text-2xl">오늘의 랭킹</h2>
        <button class="inline-flex items-center text-sm font-semibold text-[#1f4c7b] hover:underline" @click="emit('navigate', '/concerts')">
          더보기
          <ChevronRight :size="16" />
        </button>
      </div>
      <div class="grid gap-4 md:grid-cols-3">
        <article
          v-for="concert in props.concerts.slice(0, 3)"
          :key="concert.id"
          class="overflow-hidden rounded-2xl border border-[#d9e3ee] bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-lg"
        >
          <button class="block w-full text-left" @click="emit('openConcert', concert.id)">
            <div class="relative h-56 overflow-hidden">
              <img :src="concert.thumbnailImage" :alt="concert.title" class="h-full w-full object-cover" />
              <div class="absolute left-3 top-3 rounded-full bg-black/70 px-3 py-1 text-xs font-bold text-white">TOP {{ concert.ranking }}</div>
            </div>
            <div class="space-y-2 p-4">
              <h3 class="line-clamp-1 text-base font-bold text-[#162f4d]">{{ concert.title }}</h3>
              <p class="line-clamp-1 text-xs text-[#58718f]">{{ concert.subtitle }}</p>
              <div class="flex items-center justify-between text-sm">
                <span class="text-[#4b607a]">{{ concert.period }}</span>
                <span class="font-bold text-[#ff7a00]">{{ concert.minPrice.toLocaleString() }}원~</span>
              </div>
            </div>
          </button>
        </article>
      </div>
    </section>

    <section class="mt-8 rounded-2xl bg-[#f5f8fc] p-5 md:p-7">
      <div class="mb-5 flex items-center gap-2 text-[#0d2a48]">
        <Ticket :size="18" />
        <h2 class="text-lg font-extrabold md:text-xl">이 주의 추천 공연</h2>
      </div>
      <div class="grid gap-4 md:grid-cols-3">
        <button
          v-for="concert in hotConcerts"
          :key="concert.id"
          class="rounded-xl bg-white p-3 text-left shadow-sm ring-1 ring-[#dfebf7] transition hover:ring-[#9ec3e8]"
          @click="emit('openConcert', concert.id)"
        >
          <div class="mb-3 h-36 overflow-hidden rounded-lg">
            <img :src="concert.teaserImage" :alt="concert.title" class="h-full w-full object-cover" />
          </div>
          <p class="line-clamp-1 text-sm font-bold text-[#1a3757]">{{ concert.title }}</p>
          <p class="mt-1 text-xs text-[#59728f]">{{ concert.genre }} · {{ concert.venue }}</p>
        </button>
      </div>
    </section>
  </div>
</template>
