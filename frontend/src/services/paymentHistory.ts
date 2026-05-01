import type { BookingHistoryRecord } from '../types';
import { apiRequest } from './api';

interface PaymentHistoryItem {
  paymentId: string;
  paymentStatus: string;
  amount: number | null;
  orderName: string | null;
  pgOrderId: string | null;
  concertName: string | null;
  concertVenue: string | null;
  showDateTime: string | null;
  seatLabels: string[] | null;
  createdAt: string | null;
  updatedAt: string | null;
}

const toDisplayDate = (value: string | null): { date: string; session: string } => {
  if (!value) {
    return { date: '-', session: '' };
  }

  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return { date: '-', session: '' };
  }

  const date = `${parsed.getFullYear()}-${String(parsed.getMonth() + 1).padStart(2, '0')}-${String(parsed.getDate()).padStart(2, '0')}`;
  const session = `${String(parsed.getHours()).padStart(2, '0')}:${String(parsed.getMinutes()).padStart(2, '0')}`;
  return { date, session };
};

const toBookingStatus = (paymentStatus: string): BookingHistoryRecord['status'] => {
  return ['CANCELED', 'REFUNDED', 'FAILED', 'EXPIRED'].includes(paymentStatus) ? 'canceled' : 'booked';
};

export async function fetchMyPaymentHistory(token: string, userId: number): Promise<BookingHistoryRecord[]> {
  const rows = await apiRequest<PaymentHistoryItem[]>('/api/payments/history', {
    method: 'GET',
    token
  });

  return rows.map((row) => {
    const { date, session } = toDisplayDate(row.showDateTime);
    const seatLabels = Array.isArray(row.seatLabels) ? row.seatLabels : [];

    return {
      paymentId: row.paymentId,
      bookingNumber: row.pgOrderId ?? row.paymentId,
      userId,
      concertTitle: row.concertName ?? row.orderName ?? '공연 정보 없음',
      concertVenue: row.concertVenue ?? '-',
      date,
      session,
      seatLabels,
      totalAmount: typeof row.amount === 'number' ? row.amount : 0,
      status: toBookingStatus(row.paymentStatus),
      createdAt: row.updatedAt ?? row.createdAt ?? new Date(0).toISOString()
    };
  });
}
