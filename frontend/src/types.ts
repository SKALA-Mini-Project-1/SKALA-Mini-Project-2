export type Step = 'detail' | 'queue' | 'seat' | 'payment' | 'confirm' | 'mypage' | 'error' | 'soldout';

export interface Seat {
  id: string;
  section: string | null;
  row: number;
  col: number;
  price: number;
  grade: string;
}

export interface BookingData {
  date: string | null;
  session: string | null;
  seats: Seat[];
}
