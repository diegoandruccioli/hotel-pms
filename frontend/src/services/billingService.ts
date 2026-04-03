import api from './api';
import type { InvoiceRequest, InvoiceResponse, PaymentRequest, PaymentResponse } from '../types/billing.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/invoices';

export const billingService = {
  createInvoice: async (data: InvoiceRequest): Promise<InvoiceResponse> => {
    const response = await api.post<InvoiceResponse>(BASE_PATH, data);
    return response.data;
  },

  getInvoiceById: async (id: string): Promise<InvoiceResponse> => {
    const response = await api.get<InvoiceResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  processPayment: async (invoiceId: string, data: PaymentRequest): Promise<PaymentResponse> => {
    const response = await api.post<PaymentResponse>(`${BASE_PATH}/${invoiceId}/payments`, data);
    return response.data;
  },

  getAllInvoices: async (): Promise<InvoiceResponse[]> => {
    const response = await api.get<SpringPage<InvoiceResponse>>(BASE_PATH);
    return response.data.content;
  },
};
