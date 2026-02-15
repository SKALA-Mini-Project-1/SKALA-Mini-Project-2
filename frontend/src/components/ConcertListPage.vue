<script setup lang="ts">
import { CalendarDays, MapPin } from 'lucide-vue-next';
import { computed, ref } from 'vue';
import type { ConcertItem } from '../types';

const props = defineProps<{
  concerts: ConcertItem[];
}>();

const emit = defineEmits<{
  openConcert: [concertId: string];
}>();

const selectedGenre = ref('전체');
const sortKey = ref<'rank' | 'price'>('rank');

const genres = computed(() => ['전체', ...new Set(props.concerts.map((concert) => concert.genre))]);

const filteredConcerts = computed(() => {
  const byGenre =
    selectedGenre.value === '전체'
      ? props.concerts
      : props.concerts.filter((concert) => concert.genre === selectedGenre.value);

  return [...byGenre].sort((a, b) =>
    sortKey.value === 'rank' ? a.ranking - b.ranking : a.minPrice - b.minPrice
  );
});
</script>

<template>
  <div class="mx-auto max-w-[1280px] px-4 pb-12 pt-6 md:px-6 md:pb-16">
    <div class="mb-6 rounded-2xl bg-[#102946] p-6 text-white md:p-8">
      <h2 class="text-2xl font-extrabold md:text-3xl">콘서트/페스티벌</h2>
      <p class="mt-2 text-sm text-[#b8cde2]">오픈 예정 공연과 인기 공연을 한 번에 비교하고 바로 예매하세요.</p>
    </div>

    <div class="mb-6 flex flex-wrap items-center gap-2">
      <button
        v-for="genre in genres"
        :key="genre"
        class="rounded-full px-4 py-2 text-sm font-semibold transition"
        :class="
          selectedGenre === genre
            ? 'bg-[#ff7a00] text-white'
            : 'bg-[#edf3fa] text-[#365473] hover:bg-[#d8e7f7]'
        "
        @click="selectedGenre = genre"
      >
        {{ genre }}
      </button>
      <div class="ml-auto flex items-center gap-2">
        <button
          class="rounded-lg border px-3 py-2 text-sm"
          :class="sortKey === 'rank' ? 'border-[#ff7a00] text-[#ff7a00]' : 'border-[#d6e0eb] text-[#4d6784]'"
          @click="sortKey = 'rank'"
        >
          랭킹순
        </button>
        <button
          class="rounded-lg border px-3 py-2 text-sm"
          :class="sortKey === 'price' ? 'border-[#ff7a00] text-[#ff7a00]' : 'border-[#d6e0eb] text-[#4d6784]'"
          @click="sortKey = 'price'"
        >
          가격 낮은순
        </button>
      </div>
    </div>

    <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
      <article
        v-for="concert in filteredConcerts"
        :key="concert.id"
        class="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-[#dce7f3] transition hover:-translate-y-1 hover:shadow-lg"
      >
        <button class="block w-full text-left" @click="emit('openConcert', concert.id)">
          <div class="relative h-64 overflow-hidden">
            <img :src="concert.heroImage" :alt="concert.title" class="h-full w-full object-cover" />
            <div class="absolute left-3 top-3 rounded-full bg-[#0f2540]/90 px-3 py-1 text-xs font-bold text-white">No.{{ concert.ranking }}</div>
          </div>
          <div class="space-y-2 p-4">
            <h3 class="line-clamp-1 text-lg font-extrabold text-[#173350]">{{ concert.title }}</h3>
            <p class="line-clamp-1 text-xs text-[#5b7693]">{{ concert.subtitle }}</p>
            <p class="line-clamp-1 text-sm text-[#304d69]">{{ concert.artist }}</p>
            <div class="mt-3 space-y-1 text-xs text-[#5f7893]">
              <p class="flex items-center gap-1"><CalendarDays :size="14" />{{ concert.period }}</p>
              <p class="flex items-center gap-1"><MapPin :size="14" />{{ concert.venue }}</p>
            </div>
            <div class="pt-2 text-right text-sm font-extrabold text-[#ff7a00]">{{ concert.minPrice.toLocaleString() }}원 ~</div>
          </div>
        </button>
      </article>
    </div>
  </div>
</template>
