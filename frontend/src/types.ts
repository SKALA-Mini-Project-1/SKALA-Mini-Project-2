export type Step =
  | 'detail'
  | 'queue'
  | 'seat'
  | 'payment'
  | 'confirm'
  | 'mypage'
  | 'error'
  | 'soldout'

export interface ConcertDateSlot {
  day: number
  weekday: string
  isoDate: string
  isAvailable: boolean
  isSoldOut?: boolean
}

export interface ConcertSession {
  id: string
  time: string
  status: '여유' | '보통' | '매진임박'
}

export interface ConcertItem {
  id: string
  title: string
  subtitle: string
  artist: string
  venue: string
  period: string
  genre: string
  ageRating: string
  runtime: string
  heroImage: string
  thumbnailImage: string
  teaserImage: string
  tags: string[]
  minPrice: number
  ranking: number
  availableDates: ConcertDateSlot[]
  sessions: ConcertSession[]
}
export type Step =
  | 'detail'
  | 'queue'
  | 'seat'
  | 'payment'
  | 'confirm'
  | 'mypage'
  | 'error'
  | 'soldout'

export type BookingData = {
  bookingId: string;   // create에 보낼 값
  seats: Seat[];
  date: string;
  session: number;
};

// 완료 화면용 타입
export type BookingDetailResponse = {
  bookingId: string;
  bookingNumber: string;
  concertTitle: string;
  venue: string;
  date: string;
  session: number;
  seats: Seat[];
  totalAmount: number;
};
export interface ConcertDateSlot {
  day: number
  weekday: string
  isoDate: string
  isAvailable: boolean
  isSoldOut?: boolean
}

export interface ConcertSession {
  id: string
  time: string
  status: '여유' | '보통' | '매진임박'
}

export interface ConcertItem {
  id: string
  title: string
  subtitle: string
  artist: string
  venue: string
  period: string
  genre: string
  ageRating: string
  runtime: string
  heroImage: string
  thumbnailImage: string
  teaserImage: string
  tags: string[]
  minPrice: number
  ranking: number
  availableDates: ConcertDateSlot[]
  sessions: ConcertSession[]
}

export interface Seat {
  id: string
  section: string | null
  row: number
  col: number
  price: number
  grade: string
}

export interface BookingData {
  bookingId?: string | null
  bookingNumber: string | null
  concertId: string | null
  scheduleId: string | null
  concertTitle: string | null
  concertVenue: string | null
  date: string | null
  session: string | null
  seats: Seat[]
}

export interface BookingHistoryRecord {
  bookingNumber: string
  userId: number
  concertTitle: string
  concertVenue: string
  date: string
  session: string
  seatLabels: string[]
  totalAmount: number
  status: 'booked' | 'canceled'
  createdAt: string
}
