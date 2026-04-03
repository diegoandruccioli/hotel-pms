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

  exportToCsv: (report: OwnerFinancialReportDto): void => {
    const headers = ['Invoice #', 'Issue Date', 'Amount (€)', 'Status', 'Guest ID'];
    const rows = report.invoices.map((inv) => [
      inv.invoiceNumber,
      inv.issueDate ? new Date(inv.issueDate).toLocaleDateString() : '—',
      inv.totalAmount.toFixed(2),
      inv.status,
      inv.guestId,
    ]);

    const csvContent = [headers, ...rows]
      .map((row) => row.map((cell) => `"${String(cell).replace(/"/g, '""')}"`).join(','))
      .join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `owner-report-${report.startDate}-to-${report.endDate}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  },
};
