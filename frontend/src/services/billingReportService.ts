import api from './api';
import type { OwnerFinancialReportDto, OwnerFinancialSummaryDto } from '../types';
import { downloadViaIframe } from '../utils/downloadViaIframe';

const REPORT_PATH = '/api/v1/reports/owner';
const SUMMARY_PATH = '/api/v1/reports/owner/summary';
const EXPORT_CSV_PATH = '/api/v1/reports/owner/export.csv';

export const billingReportService = {
  getOwnerFinancialReport: async (
    startDate: string,
    endDate: string
  ): Promise<OwnerFinancialReportDto> => {
    const response = await api.get<OwnerFinancialReportDto>(REPORT_PATH, {
      params: { startDate, endDate },
    });
    return response.data;
  },

  /** Aggregates-only counterpart of {@link getOwnerFinancialReport} — no
   * per-invoice list, backs the Dashboard's revenue widget. */
  getOwnerFinancialSummary: async (
    startDate: string,
    endDate: string
  ): Promise<OwnerFinancialSummaryDto> => {
    const response = await api.get<OwnerFinancialSummaryDto>(SUMMARY_PATH, {
      params: { startDate, endDate },
    });
    return response.data;
  },

  /** Downloads the owner financial report for the given period as a CSV file,
   * generated server-side, via a hidden iframe. Owner/Admin only. */
  exportToCsv: (startDate: string, endDate: string): void => {
    const params = new URLSearchParams({ startDate, endDate });
    downloadViaIframe(`${EXPORT_CSV_PATH}?${params.toString()}`);
  },
};
