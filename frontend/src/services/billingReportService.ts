import api from './api';
import type { OwnerFinancialReportDto } from '../types/ownerReport.types';

const REPORT_PATH = '/api/v1/reports/owner';

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

  exportToCsv: (report: OwnerFinancialReportDto, t: (key: string) => string): void => {
    const headers = [
      t('invoice_number'),
      t('issue_date'),
      t('amount'),
      t('status'),
      t('guest_id'),
    ];
    const rows = report.invoices.map((inv) => [
      inv.invoiceNumber,
      inv.issueDate ? new Date(inv.issueDate).toLocaleDateString() : '—',
      inv.totalAmount.toFixed(2),
      t(`invoice_status_${inv.status}`),
      inv.guestId,
    ]);

    // Excel on Windows opens CSV with the OS locale's decimal separator; in it-IT that's
    // a comma, so a plain comma-delimited file collapses into a single column. A leading
    // UTF-8 BOM plus a semicolon delimiter is what Excel-IT actually expects.
    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(';'))
      .join('\n');

    const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `owner-report-${report.startDate}-to-${report.endDate}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  },
};
