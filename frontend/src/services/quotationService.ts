import api from './api';
import type { QuotationRequest, QuotationResponse } from '../types/quotation.types';
import type { ReservationResponse } from '../types/reservation.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/quotations';
const IFRAME_CLEANUP_DELAY_MS = 30000;

export const quotationService = {
  getAllQuotations: async (page = 0, size = 20): Promise<SpringPage<QuotationResponse>> => {
    const response = await api.get<SpringPage<QuotationResponse>>(`${BASE_PATH}?page=${page}&size=${size}&sort=createdAt,desc`);
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

  sendQuotation: async (id: string): Promise<QuotationResponse> => {
    const response = await api.post<QuotationResponse>(`${BASE_PATH}/${id}/send`, {});
    return response.data;
  },

  convertToReservation: async (id: string): Promise<ReservationResponse> => {
    const response = await api.post<ReservationResponse>(`${BASE_PATH}/${id}/convert`, {});
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
   * why fetch+Blob+synthetic-click was abandoned).
   */
  downloadPdf: (id: string): void => {
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `${BASE_PATH}/${id}/pdf`;
    document.body.appendChild(iframe);
    setTimeout(() => document.body.removeChild(iframe), IFRAME_CLEANUP_DELAY_MS);
  },
};
