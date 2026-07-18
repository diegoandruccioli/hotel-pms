export type InvoiceStatus = 'ISSUED' | 'PAID' | 'CANCELLED';
export type DocumentType = 'FATTURA' | 'RICEVUTA';
export type SdiStatus = 'NOT_SENT' | 'SENT' | 'ACCEPTED' | 'REJECTED';

export type PaymentMethod =
  | 'CASH'
  | 'CREDIT_CARD'
  | 'DEBIT_CARD'
  | 'BANK_TRANSFER'
  | 'CHECK';

export type ChargeType = 'FB_ORDER' | 'ROOM_NIGHT' | 'EXTRA';

export interface ChargeResponse {
  id: string;
  invoiceId?: string;
  type: ChargeType;
  description?: string;
  amount: number;
  vatRate?: number;
  referenceId?: string;
  createdAt?: string;
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

export interface InvoiceResponse {
  id: string;
  invoiceNumber: string;
  issueDate: string;
  totalAmount: number;
  status: InvoiceStatus;
  documentType: DocumentType;
  sdiStatus: SdiStatus;
  reservationId: string;
  guestId: string;
  stayId?: string;
  payments: PaymentResponse[];
  charges?: ChargeResponse[];
}

/** A single invoice search result (C12): the invoice plus a resolved guest display name. */
export interface InvoiceSearchResult {
  invoice: InvoiceResponse;
  guestName: string | null;
}
