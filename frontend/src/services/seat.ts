import { apiRequest } from './api';
import { getToken } from './auth';

export interface SeatMapItem {
  seatId: number;
  section: string;
  rowNumber: number;
  seatNumber: number;
  status: string;
  isHeldByMe?: boolean | null;
}

interface SeatMapResponse {
  concertId: number;
  seatCount: number;
  seats: SeatMapItem[];
}

export const getSeatMap = async (concertId: number) => {
  const token = getToken();

  return apiRequest<SeatMapResponse>(`/api/concerts/${concertId}/seats`, {
    method: 'GET',
    ...(token ? { token } : {})
  });
};

export const getSeatMapBySchedule = async (scheduleId: number) => {
  const token = getToken();

  return apiRequest<SeatMapResponse>(`/api/concerts/schedules/${scheduleId}/seats`, {
    method: 'GET',
    ...(token ? { token } : {})
  });
};

export const holdSeat = async (scheduleId: number, section: string, rowNumber: number, seatNumber: number) => {
  const token = getToken();

  return apiRequest<unknown>(`/api/seats/hold`, {
    method: 'POST',
    body: JSON.stringify({ scheduleId, section, rowNumber, seatNumber }),
    ...(token ? { token } : {})
  });
};
