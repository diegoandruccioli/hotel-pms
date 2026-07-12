import api from './api';
import type { DocumentType, InvoiceResponse, PaymentRequest, PaymentResponse, SdiStatus } from '../types/billing.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/invoices';
const IFRAME_CLEANUP_DELAY_MS = 10000;

export const billingService = {
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

  updateDocumentType: async (invoiceId: string, documentType: DocumentType): Promise<InvoiceResponse> => {
    const response = await api.patch<InvoiceResponse>(`${BASE_PATH}/${invoiceId}/document-type`, { documentType });
    return response.data;
  },

  updateSdiStatus: async (invoiceId: string, sdiStatus: SdiStatus): Promise<InvoiceResponse> => {
    const response = await api.patch<InvoiceResponse>(`${BASE_PATH}/${invoiceId}/sdi-status`, { sdiStatus });
    return response.data;
  },

  downloadFatturaPAXml: (invoiceId: string): void => {
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `${BASE_PATH}/${invoiceId}/fatturaPA`;
    document.body.appendChild(iframe);
    setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
  },

  /**
   * Triggers the browser's native download for the invoice PDF via a hidden iframe.
   *
   * Deliberately NOT fetch+Blob+synthetic-<a>-click: that pattern was verified end-to-end
   * (network 200, valid PDF bytes, anchor configured correctly, click() invoked) yet
   * produced no visible file save in real Chrome — a known failure mode for synthetic
   * clicks on blob: URLs (silently dropped by some browsers/extensions, no JS-visible
   * error). A hidden iframe pointed straight at the endpoint relies on the server's
   * `Content-Disposition: attachment` header to trigger the OS-level native download,
   * the same mechanism as a real <a href> click — and keeps any non-download error
   * response (e.g. a 500) contained inside the iframe instead of navigating the SPA away.
   */
  downloadPdf: (invoiceId: string): void => {
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `${BASE_PATH}/${invoiceId}/pdf`;
    document.body.appendChild(iframe);
    setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
  },
};
