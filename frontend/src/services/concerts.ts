import type { ConcertItem, ConcertSession, ConcertDateSlot } from '../types';
import { apiRequest } from './api';

interface ConcertScheduleApi {
  id: number;
  startTime: string;
  endTime: string;
  totalSeats: number;
}

interface ConcertApi {
  id: number;
  title: string;
  category: string;
  description: string;
  location: string;
  durationMinutes: number;
  artistName: string | null;
  minPrice: number | null;
  schedules: ConcertScheduleApi[];
}

const IMAGE_SET = [
  {
    hero: 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80',
    thumb: 'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?auto=format&fit=crop&w=600&q=80',
    teaser: 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=80'
  },
  {
    hero: 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=1200&q=80',
    thumb: 'https://images.unsplash.com/photo-1501612780327-45045538702b?auto=format&fit=crop&w=600&q=80',
    teaser: 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=80'
  },
  {
    hero: 'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?auto=format&fit=crop&w=1200&q=80',
    thumb: 'https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=600&q=80',
    teaser: 'https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?auto=format&fit=crop&w=900&q=80'
  }
];

const pad = (v: number) => String(v).padStart(2, '0');

const toPeriod = (schedules: ConcertScheduleApi[]) => {
  if (schedules.length === 0) {
    return '-';
  }
  const sorted = [...schedules].sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
  const first = new Date(sorted[0].startTime);
  const last = new Date(sorted[sorted.length - 1].startTime);
  const format = (d: Date) => `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
  return `${format(first)} - ${format(last)}`;
};

const toDates = (schedules: ConcertScheduleApi[]): ConcertDateSlot[] => {
  const map = new Map<string, Date>();
  schedules.forEach((schedule) => {
    const d = new Date(schedule.startTime);
    const key = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    if (!map.has(key)) {
      map.set(key, d);
    }
  });

  const weekday = ['일', '월', '화', '수', '목', '금', '토'];
  return [...map.values()]
    .sort((a, b) => a.getTime() - b.getTime())
    .map((d) => ({
      day: d.getDate(),
      weekday: weekday[d.getDay()],
      isoDate: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
      isAvailable: true
    }));
};

const toSessions = (schedules: ConcertScheduleApi[]): ConcertSession[] =>
  schedules
    .slice()
    .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime())
    .map((schedule) => {
      const start = new Date(schedule.startTime);
      const hh = pad(start.getHours());
      const mm = pad(start.getMinutes());
      return {
        id: String(schedule.id),
        time: `${hh}:${mm}`,
        status: '보통' as const
      };
    });

const mapConcert = (concert: ConcertApi, index: number): ConcertItem => {
  const images = IMAGE_SET[index % IMAGE_SET.length];
  return {
    id: String(concert.id),
    title: concert.title,
    subtitle: concert.artistName ?? concert.category,
    artist: concert.artistName ?? 'Various Artists',
    venue: concert.location,
    period: toPeriod(concert.schedules),
    genre: concert.category,
    ageRating: '전체관람가',
    runtime: `${concert.durationMinutes}분`,
    heroImage: images.hero,
    thumbnailImage: images.thumb,
    teaserImage: images.teaser,
    tags: ['모바일티켓'],
    minPrice: concert.minPrice ?? 0,
    ranking: index + 1,
    availableDates: toDates(concert.schedules),
    sessions: toSessions(concert.schedules)
  };
};

export const fetchConcerts = async (): Promise<ConcertItem[]> => {
  const concerts = await apiRequest<ConcertApi[]>('/api/concerts', { method: 'GET' });
  return concerts.map((concert, index) => mapConcert(concert, index));
};
