import { apiRequest } from './api';
import { getToken } from './auth';

export interface SeatMapItem {
  seatId: number;
  section: string;
  rowNumber: number;
  seatNumber: number;
  status: string;
  displayStatus?: string | null;
  isHeldByMe?: boolean | null;
}

export interface SeatHoldResponse {
  action?: 'held' | 'released';
  status?: string;
  message?: string;
}

export interface BatchSeatHoldResponse {
  status?: string;
  message?: string;
}

export interface SeatSectionSummaryItem {
  section: string;
  seatCount: number;
  reservedSeatCount: number;
  availableSeatCount: number;
  rowCount: number;
  colCount: number;
  grade: string;
  price: number;
}

interface SeatMapResponse {
  concertId: number;
  seatCount: number;
  seatAccessTtlSeconds?: number;
  seats: SeatMapItem[];
}

export interface SeatSectionSummaryResponse {
  concertId: number;
  scheduleId: number;
  totalSeatCount: number;
  sectionCount: number;
  seatAccessTtlSeconds?: number;
  sections: SeatSectionSummaryItem[];
}

export interface SeatSectionDetailResponse {
  concertId: number;
  scheduleId: number;
  section: string;
  seatCount: number;
  seatAccessTtlSeconds?: number;
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

export const getSeatSectionSummaryBySchedule = async (scheduleId: number) => {
  const token = getToken();

  return apiRequest<SeatSectionSummaryResponse>(`/api/concerts/schedules/${scheduleId}/seat-summary`, {
    method: 'GET',
    ...(token ? { token } : {})
  });
};

export const getSeatSectionDetailBySchedule = async (scheduleId: number, section: string) => {
  const token = getToken();

  return apiRequest<SeatSectionDetailResponse>(`/api/concerts/schedules/${scheduleId}/sections/${encodeURIComponent(section)}/seats`, {
    method: 'GET',
    ...(token ? { token } : {})
  });
};

export const holdSeat = async (scheduleId: number, section: string, rowNumber: number, seatNumber: number) => {
  const token = getToken();

  return apiRequest<SeatHoldResponse>(`/api/seats/hold`, {
    method: 'POST',
    body: JSON.stringify({ scheduleId, section, rowNumber, seatNumber }),
    ...(token ? { token } : {})
  });
};

export const holdSeatsBatch = async (concertId: number, seatIds: number[]) => {
  const token = getToken();

  return apiRequest<BatchSeatHoldResponse>(`/api/seats/holds`, {
    method: 'POST',
    body: JSON.stringify({ concertId, seatIds }),
    ...(token ? { token } : {})
  });
};

export const leaveSeatScreen = async (concertId: number, scheduleId: number) => {
  const token = getToken();

  return apiRequest<{ status: string }>(
    `/api/seats/leave?concertId=${concertId}&scheduleId=${scheduleId}`,
    {
      method: 'POST',
      keepalive: true,
      ...(token ? { token } : {})
    }
  );
};
