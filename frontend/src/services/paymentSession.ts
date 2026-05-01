export interface ActivePaymentSession {
  paymentId: string;
  bookingId: string;
}

const ACTIVE_PAYMENT_SESSION_KEY = 'fairline_active_payment_session';

export function getActivePaymentSession(): ActivePaymentSession | null {
  const raw = sessionStorage.getItem(ACTIVE_PAYMENT_SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as ActivePaymentSession;
  } catch {
    sessionStorage.removeItem(ACTIVE_PAYMENT_SESSION_KEY);
    return null;
  }
}

export function setActivePaymentSession(session: ActivePaymentSession): void {
  sessionStorage.setItem(ACTIVE_PAYMENT_SESSION_KEY, JSON.stringify(session));
}

export function clearActivePaymentSession(): void {
  sessionStorage.removeItem(ACTIVE_PAYMENT_SESSION_KEY);
}
