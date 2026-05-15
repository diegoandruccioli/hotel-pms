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

  downloadPdf: async (invoiceId: string, invoiceNumber: string): Promise<void> => {
    const response = await api.get(`${BASE_PATH}/${invoiceId}/pdf`, {
      responseType: 'blob',
    });
    const url = URL.createObjectURL(new Blob([response.data as BlobPart], { type: 'application/pdf' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${invoiceNumber}.pdf`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  },
};
