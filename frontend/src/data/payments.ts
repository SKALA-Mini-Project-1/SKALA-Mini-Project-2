export type PaymentCreateRequest = {
  bookingId: string;
  userId: number;
};

export type PaymentCreateResponse = {
  paymentId: string;
  status: string;
  createdAt: string;
  expiredAt: string;
};

export type PaymentSubmitResponse = {
  id: string;
  status: string;
  expiredAt: string;
  idempotencyKey: string;
  updatedAt: string;
  bookingId: string;
  amount: number;
  orderId: string;
  customerKey: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
};

export async function createPayment(req: PaymentCreateRequest, token: string): Promise<PaymentCreateResponse> {
  const res = await fetch("/api/payments/create", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(req),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `create payment failed (${res.status})`);
  }

  return await res.json();
}

export async function submitPayment(paymentId: string, token: string): Promise<PaymentSubmitResponse> {
  const res = await fetch(`/api/payments/${paymentId}/submit`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`
    }
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `submit payment failed (${res.status})`);
  }

  return await res.json();
}
