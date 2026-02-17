import { apiRequest } from './api';
import { getToken } from './auth';

export interface CreateBookingResponse {
  bookingId: string;
}

export const createBooking = async (concertId: number, seatIds: number[]) => {
  const token = getToken();

  return apiRequest<CreateBookingResponse>(`/api/bookings`, {
    method: 'POST',
    body: JSON.stringify({ concertId, seatIds }),
    ...(token ? { token } : {})
  });
};
