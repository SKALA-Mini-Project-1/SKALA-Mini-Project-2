import type { ConcertItem } from '../types'

export const concerts: ConcertItem[] = [
  {
    id: '1',
    title: 'IU 2026 HEREH',
    subtitle: 'WORLD TOUR ENCORE',
    artist: '아이유 (IU)',
    venue: '서울월드컵경기장',
    period: '2026.03.21 - 2026.03.22',
    genre: '콘서트',
    ageRating: '8세 이상 관람가',
    runtime: '150분',
    heroImage:
      'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=80',
    thumbnailImage:
      'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?auto=format&fit=crop&w=600&q=80',
    teaserImage:
      'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=80',
    tags: ['단독판매', 'MD 패키지', '모바일티켓'],
    minPrice: 88000,
    ranking: 1,
    availableDates: [
      { day: 21, weekday: '토', isoDate: '2026-03-21', isAvailable: true },
      { day: 22, weekday: '일', isoDate: '2026-03-22', isAvailable: true, isSoldOut: true },
      { day: 28, weekday: '토', isoDate: '2026-03-28', isAvailable: true },
    ],
    sessions: [
      { id: '1', time: '17:00', status: '매진임박' },
      { id: '2', time: '20:00', status: '보통' },
    ],
  },
  {
    id: 'day6-finale-2026',
    title: 'DAY6 FINALE',
    subtitle: 'FOREVER YOUNG',
    artist: 'DAY6',
    venue: 'KSPO DOME',
    period: '2026.04.04 - 2026.04.05',
    genre: '콘서트',
    ageRating: '전체관람가',
    runtime: '130분',
    heroImage:
      'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=1200&q=80',
    thumbnailImage:
      'https://images.unsplash.com/photo-1501612780327-45045538702b?auto=format&fit=crop&w=600&q=80',
    teaserImage:
      'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=80',
    tags: ['팬클럽 선예매', '현장수령 가능'],
    minPrice: 77000,
    ranking: 2,
    availableDates: [
      { day: 4, weekday: '토', isoDate: '2026-04-04', isAvailable: true },
      { day: 5, weekday: '일', isoDate: '2026-04-05', isAvailable: true },
    ],
    sessions: [
      { id: '1', time: '18:00', status: '보통' },
      { id: '2', time: '20:30', status: '여유' },
    ],
  },
  {
    id: 'jazz-night-2026',
    title: 'SEOUL JAZZ NIGHT',
    subtitle: 'SPRING SPECIAL',
    artist: 'Various Artists',
    venue: '올림픽홀',
    period: '2026.05.09 - 2026.05.10',
    genre: '재즈/페스티벌',
    ageRating: '12세 이상 관람가',
    runtime: '180분',
    heroImage:
      'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?auto=format&fit=crop&w=1200&q=80',
    thumbnailImage:
      'https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=600&q=80',
    teaserImage:
      'https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?auto=format&fit=crop&w=900&q=80',
    tags: ['할인쿠폰', '좌석지정'],
    minPrice: 66000,
    ranking: 3,
    availableDates: [
      { day: 9, weekday: '토', isoDate: '2026-05-09', isAvailable: true },
      { day: 10, weekday: '일', isoDate: '2026-05-10', isAvailable: true },
    ],
    sessions: [
      { id: '1', time: '16:00', status: '여유' },
      { id: '2', time: '19:30', status: '보통' },
    ],
  },
  {
    id: 'hiphop-ground-2026',
    title: 'HIPHOP GROUND',
    subtitle: 'SEOUL OPEN AIR',
    artist: "ZICO, DPR, BE'O 외",
    venue: '잠실종합운동장',
    period: '2026.06.13',
    genre: '힙합/페스티벌',
    ageRating: '15세 이상 관람가',
    runtime: '240분',
    heroImage:
      'https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?auto=format&fit=crop&w=1200&q=80',
    thumbnailImage:
      'https://images.unsplash.com/photo-1565031491910-e57fac031c41?auto=format&fit=crop&w=600&q=80',
    teaserImage:
      'https://images.unsplash.com/photo-1565031491910-e57fac031c41?auto=format&fit=crop&w=900&q=80',
    tags: ['오늘오픈', '스탠딩'],
    minPrice: 99000,
    ranking: 4,
    availableDates: [{ day: 13, weekday: '토', isoDate: '2026-06-13', isAvailable: true }],
    sessions: [{ id: '1', time: '17:30', status: '보통' }],
  },
]

export const getConcertById = (id: string | null) =>
  concerts.find((concert) => concert.id === id) ?? concerts[0]
