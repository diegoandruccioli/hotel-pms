import { describe, it, expect, vi, beforeEach } from 'vitest';
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

  it('should export report to CSV', () => {
    const mockReport = {
      startDate: '2026-01-01',
      endDate: '2026-03-31',
      totalRevenue: 500,
      invoices: [
        { invoiceNumber: 'INV-001', issueDate: '2026-01-15T00:00:00', totalAmount: 500, status: 'PAID', guestId: 'g1' },
      ],
    };

    const createObjectURL = vi.fn(() => 'blob:http://test/123');
    const revokeObjectURL = vi.fn();
    const clickFn = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });

    const appendChildSpy = vi.spyOn(document, 'createElement').mockReturnValue({
      href: '',
      download: '',
      click: clickFn,
    } as unknown as HTMLAnchorElement);

    billingReportService.exportToCsv(mockReport as never);

    expect(createObjectURL).toHaveBeenCalled();
    expect(clickFn).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/123');
    appendChildSpy.mockRestore();
  });
});
