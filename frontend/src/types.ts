export type Step = 'detail' | 'queue' | 'seat' | 'payment' | 'confirm' | 'mypage' | 'error' | 'soldout';

// 원본 코드
// export interface Seat {
//   id: string;
//   section: string | null;
//   row: number;
//   col: number;
//   price: number;
//   grade: string;
// }

// export interface BookingData {
//   date: string | null;
//   session: string | null;
//   seats: Seat[];
// }

export type Seat = {
  id: string;          // seatId로 보낼 값
  grade: string;
  section: string;
  row: string;
  col: string;
  price: number;
};

export type BookingData = {
  bookingId: string;   // create에 보낼 값
  seats: Seat[];
  date: string;
  session: number;
};