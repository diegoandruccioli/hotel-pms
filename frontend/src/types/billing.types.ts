export type InvoiceStatus = 'ISSUED' | 'PAID' | 'CANCELLED';

export type PaymentMethod =
  | 'CASH'
  | 'CREDIT_CARD'
  | 'DEBIT_CARD'
  | 'BANK_TRANSFER'
  | 'CHECK';

export type ChargeType = 'FB_ORDER' | 'ROOM_SERVICE' | 'OTHER';

export interface ChargeResponse {
  id: string;
  type: ChargeType;
  description?: string;
  amount: number;
  referenceId?: string;
}

export interface PaymentRequest {
  amount: number;
  paymentMethod: PaymentMethod;
  transactionReference?: string;
}

export interface PaymentResponse {
  id: string;
  paymentDate: string;
  amount: number;
  paymentMethod: PaymentMethod;
  transactionReference?: string;
  invoiceId: string;
}

export interface InvoiceRequest {
  reservationId: string;
  guestId: string;
  totalAmount: number;
  status: InvoiceStatus;
}

export interface InvoiceResponse {
  id: string;
  invoiceNumber: string;
  issueDate: string;
  totalAmount: number;
  status: InvoiceStatus;
  reservationId: string;
  guestId: string;
  stayId?: string;
  payments: PaymentResponse[];
  charges?: ChargeResponse[];
}
