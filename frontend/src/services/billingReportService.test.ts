import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import api from './api';
import { billingReportService } from './billingReportService';

vi.mock('./api');

describe('billingReportService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch owner financial report', async () => {
    const mockReport = {
      startDate: '2026-01-01',
      endDate: '2026-03-31',
      totalRevenue: 10000,
      invoices: [{ invoiceNumber: 'INV-001', totalAmount: 500, status: 'PAID', guestId: 'g1', issueDate: '2026-01-15' }],
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockReport });

    const result = await billingReportService.getOwnerFinancialReport('2026-01-01', '2026-03-31');

    expect(api.get).toHaveBeenCalledWith('/api/v1/reports/owner', {
      params: { startDate: '2026-01-01', endDate: '2026-03-31' },
    });
    expect(result).toEqual(mockReport);
  });

  it('should fetch owner financial summary', async () => {
    const mockSummary = {
      startDate: '2000-01-01',
      endDate: '2099-12-31',
      totalRevenue: 50000,
      totalInvoices: 300,
      paidInvoices: 280,
      pendingRevenue: 10000,
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockSummary });

    const result = await billingReportService.getOwnerFinancialSummary('2000-01-01', '2099-12-31');

    expect(api.get).toHaveBeenCalledWith('/api/v1/reports/owner/summary', {
      params: { startDate: '2000-01-01', endDate: '2099-12-31' },
    });
    expect(result).toEqual(mockSummary);
  });

  it('downloads the server-generated CSV export via a hidden iframe', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({});

    await billingReportService.exportToCsv('2026-01-01', '2026-03-31');

    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.src).toContain('/api/v1/reports/owner/export.csv?startDate=2026-01-01&endDate=2026-03-31');
  });

  afterEach(() => {
    document.body.replaceChildren();
  });
});
