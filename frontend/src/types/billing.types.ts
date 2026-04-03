export type InvoiceStatus = 'ISSUED' | 'PAID' | 'CANCELLED';

export type PaymentMethod = 'CREDIT_CARD' | 'CASH' | 'TRANSFER';

export interface PaymentRequest {
  amount: number;
  paymentMethod: PaymentMethod;
  transactionReference: string;
}

export interface PaymentResponse {
  id: string; // UUID
  paymentDate: string; // ISO DateTime
  amount: number;
  paymentMethod: PaymentMethod;
  transactionReference: string;
  invoiceId: string;
}

export interface InvoiceRequest {
  reservationId: string;
  guestId: string;
  totalAmount: number;
  status: InvoiceStatus;
}

export interface InvoiceResponse {
  id: string; // UUID
  invoiceNumber: string;
  issueDate: string; // ISO DateTime
  totalAmount: number;
  status: InvoiceStatus;
  reservationId: string;
  guestId: string;
  payments: PaymentResponse[];
}
