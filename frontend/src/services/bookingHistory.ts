import type { BookingHistoryRecord } from '../types';

const BOOKING_HISTORY_KEY = 'fairline_booking_history_v1';

const readAll = (): BookingHistoryRecord[] => {
  const raw = localStorage.getItem(BOOKING_HISTORY_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed as BookingHistoryRecord[];
  } catch {
    return [];
  }
};

const writeAll = (records: BookingHistoryRecord[]) => {
  localStorage.setItem(BOOKING_HISTORY_KEY, JSON.stringify(records));
};

const createDummyRecords = (userId: number): BookingHistoryRecord[] => {
  const now = Date.now();
  return [
    {
      bookingNumber: `BL-${userId}-20260214-001`,
      userId,
      concertTitle: 'DAY6 3RD WORLD TOUR',
      concertVenue: 'KSPO DOME',
      date: '2026-02-14',
      session: '1',
      seatLabels: ['R석 B-12', 'R석 B-13'],
      totalAmount: 286000,
      status: 'booked',
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 3).toISOString()
    },
    {
      bookingNumber: `BL-${userId}-20260201-002`,
      userId,
      concertTitle: 'Coldplay Live in Seoul',
      concertVenue: '서울월드컵경기장',
      date: '2026-02-01',
      session: '2',
      seatLabels: ['S석 C-07'],
      totalAmount: 132000,
      status: 'canceled',
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 10).toISOString()
    },
    {
      bookingNumber: `BL-${userId}-20260120-003`,
      userId,
      concertTitle: 'YOASOBI ASIA TOUR',
      concertVenue: '올림픽공원 체조경기장',
      date: '2026-01-20',
      session: '1',
      seatLabels: ['VIP석 A-01'],
      totalAmount: 198000,
      status: 'booked',
      createdAt: new Date(now - 1000 * 60 * 60 * 24 * 24).toISOString()
    }
  ];
};

export const addBookingHistory = (record: BookingHistoryRecord) => {
  const records = readAll();
  records.unshift(record);
  writeAll(records);
};

export const ensureDummyBookingHistory = (userId: number) => {
  const records = readAll();
  const hasAnyForUser = records.some((record) => record.userId === userId);
  if (hasAnyForUser) {
    return;
  }

  writeAll([...createDummyRecords(userId), ...records]);
};

export const getBookingHistoryByUser = (userId: number) =>
  readAll()
    .filter((record) => record.userId === userId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

export const cancelBookingHistory = (userId: number, bookingNumber: string) => {
  const records = readAll();
  const updated = records.map((record) =>
    record.userId === userId && record.bookingNumber === bookingNumber
      ? { ...record, status: 'canceled' as const }
      : record
  );
  writeAll(updated);
};
