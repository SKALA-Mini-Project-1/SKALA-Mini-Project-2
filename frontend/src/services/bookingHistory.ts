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

export const addBookingHistory = (record: BookingHistoryRecord) => {
  const records = readAll();
  records.unshift(record);
  writeAll(records);
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
