import api from './api';
import type { BusinessDateResponse, HousekeepingWorksheetResponse } from '../types';
import { downloadViaIframe } from '../utils/downloadViaIframe';

const BASE_PATH = '/api/v1/frontdesk/housekeeping';

export const housekeepingService = {
  /** The hotel's currently resolved housekeeping business date, plus the
   * timezone/cutoff settings behind it — see `useBusinessDate`. */
  getBusinessDate: async (): Promise<BusinessDateResponse> => {
    const response = await api.get<BusinessDateResponse>(`${BASE_PATH}/business-date`);
    return response.data;
  },

  /** The worksheet as JSON, for the on-screen preview before download. */
  getWorksheet: async (date: string): Promise<HousekeepingWorksheetResponse> => {
    const response = await api.get<HousekeepingWorksheetResponse>(`${BASE_PATH}/worksheet`, {
      params: { date },
    });
    return response.data;
  },

  /** Downloads the worksheet PDF via a hidden iframe — see `downloadViaIframe`. */
  downloadWorksheetPdf: (date: string): void => {
    downloadViaIframe(`${BASE_PATH}/worksheet.pdf?date=${encodeURIComponent(date)}`);
  },
};
