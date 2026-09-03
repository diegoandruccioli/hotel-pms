import api from './api';
import type {
  BillingDocumentType as DocumentType,
  InvoiceResponse,
  InvoiceSearchResult,
  InvoiceStatus,
  PaymentRequest,
  PaymentResponse,
  SdiStatus,
  SpringPage,
} from '../types';

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

  searchInvoices: async (params: {
    status?: InvoiceStatus;
    query?: string;
    dateFrom?: string;
    dateTo?: string;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<SpringPage<InvoiceSearchResult>> => {
    const searchParams = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.status) searchParams.set('status', params.status);
    if (params.query?.trim()) searchParams.set('query', params.query.trim());
    if (params.dateFrom) searchParams.set('dateFrom', params.dateFrom);
    if (params.dateTo) searchParams.set('dateTo', params.dateTo);
    if (params.sort) searchParams.set('sort', params.sort);
    const response = await api.get<SpringPage<InvoiceSearchResult>>(
      `${BASE_PATH}/search?${searchParams.toString()}`,
    );
    return response.data;
  },

  updateDocumentType: async (invoiceId: string, documentType: DocumentType): Promise<InvoiceResponse> => {
    const response = await api.patch<InvoiceResponse>(`${BASE_PATH}/${invoiceId}/document-type`, { documentType });
    return response.data;
  },

  updateSdiStatus: async (invoiceId: string, sdiStatus: SdiStatus): Promise<InvoiceResponse> => {
    const response = await api.patch<InvoiceResponse>(`${BASE_PATH}/${invoiceId}/sdi-status`, { sdiStatus });
    return response.data;
  },

  /**
   * Preflight check for `downloadFatturaPAXml` — runs the same validation the real
   * download would (hotel/guest fiscal identity and address, invoice eligibility),
   * without generating a recorded export or locking the invoice. Throws (with a real
   * axios error the caller can extract `response.data.detail` from) if the download
   * would be rejected, so the caller can surface it before triggering the iframe.
   */
  validateFatturaPAXml: async (invoiceId: string): Promise<void> => {
    await api.get(`${BASE_PATH}/${invoiceId}/fatturaPA/validate`);
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
