import api from './api';
import { downloadViaIframe } from './downloadViaIframe';
import type { QuotationRequest, QuotationResponse, QuotationStatus } from '../types';
import type { ReservationResponse } from '../types';
import type { SpringPage } from '../types';

const BASE_PATH = '/api/v1/quotations';
const IFRAME_CLEANUP_DELAY_MS = 30000;

export const quotationService = {
  /**
   * One page of the list, newest first (the server's default order), narrowed by status and a
   * free-text query (guest name or email). `EXPIRED` is a valid status here although the server
   * never stores it. Filter values left out are not sent.
   */
  searchQuotations: async (params: {
    status?: QuotationStatus;
    query?: string;
    page?: number;
    size?: number;
  }): Promise<SpringPage<QuotationResponse>> => {
    const searchParams = new URLSearchParams({
      page: String(params.page ?? 0),
      size: String(params.size ?? 20),
    });
    if (params.status) searchParams.set('status', params.status);
    if (params.query?.trim()) searchParams.set('query', params.query.trim());
    const response = await api.get<SpringPage<QuotationResponse>>(`${BASE_PATH}/search?${searchParams.toString()}`);
    return response.data;
  },

  getQuotationById: async (id: string): Promise<QuotationResponse> => {
    const response = await api.get<QuotationResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  createQuotation: async (data: QuotationRequest): Promise<QuotationResponse> => {
    const response = await api.post<QuotationResponse>(BASE_PATH, data);
    return response.data;
  },

  updateQuotation: async (id: string, data: QuotationRequest): Promise<QuotationResponse> => {
    const response = await api.put<QuotationResponse>(`${BASE_PATH}/${id}`, data);
    return response.data;
  },

  duplicateQuotation: async (id: string): Promise<QuotationResponse> => {
    const response = await api.post<QuotationResponse>(`${BASE_PATH}/${id}/duplicate`, {});
    return response.data;
  },

  sendQuotation: async (id: string): Promise<QuotationResponse> => {
    const response = await api.post<QuotationResponse>(`${BASE_PATH}/${id}/send`, {});
    return response.data;
  },

  convertToReservation: async (id: string, optionId?: string): Promise<ReservationResponse> => {
    const response = await api.post<ReservationResponse>(
      `${BASE_PATH}/${id}/convert`,
      optionId ? { optionId } : {},
    );
    return response.data;
  },

  declineQuotation: async (id: string): Promise<QuotationResponse> => {
    const response = await api.post<QuotationResponse>(`${BASE_PATH}/${id}/decline`, {});
    return response.data;
  },

  deleteQuotation: async (id: string): Promise<void> => {
    await api.delete(`${BASE_PATH}/${id}`);
  },

  /**
   * Triggers the browser's native download for the quotation PDF via a hidden
   * iframe — same pattern as billingService.downloadPdf (see that file for
   * why fetch+Blob+synthetic-click was abandoned), via the shared
   * `downloadViaIframe` helper so this download also benefits from its
   * pre-flight auth-refresh (BUG-5).
   */
  downloadPdf: async (id: string): Promise<void> => {
    await downloadViaIframe(`${BASE_PATH}/${id}/pdf`, IFRAME_CLEANUP_DELAY_MS);
  },

  /**
   * Fetches the quotation PDF as a Blob for inline preview (an `<iframe>` on a
   * blob: URL, revoked by the caller when the preview closes). Unlike
   * `downloadPdf`, this is meant to be *rendered*, not saved, so the iframe
   * pointed straight at the endpoint isn't a fit — `Content-Disposition:
   * attachment` would just trigger a second, unwanted download.
   */
  getPdfBlob: async (id: string): Promise<Blob> => {
    const response = await api.get(`${BASE_PATH}/${id}/pdf`, { responseType: 'blob' });
    return response.data;
  },
};
