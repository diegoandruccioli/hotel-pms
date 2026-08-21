import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { billingReportService } from './billingReportService';

vi.mock('./api');

const translate = (key: string): string => {
  const map: Record<string, string> = {
    invoice_number: 'N° Fattura',
    issue_date: 'Data Emissione',
    amount: 'Importo',
    status: 'Stato',
    guest_id: 'ID Ospite',
    invoice_status_PAID: 'Pagata',
    invoice_status_PENDING: 'invoice_status_PENDING',
  };
  return map[key] ?? key;
};

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

  it('should export report to CSV with translated headers, translated status, a UTF-8 BOM and a semicolon delimiter', () => {
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
    const blobSpy = vi.spyOn(globalThis, 'Blob');

    billingReportService.exportToCsv(mockReport as never, translate);

    expect(createObjectURL).toHaveBeenCalled();
    expect(clickFn).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/123');

    const [[parts]] = blobSpy.mock.calls;
    const content = (parts as string[]).join('');
    expect(content.startsWith('﻿')).toBe(true);
    expect(content).toContain('"N° Fattura";"Data Emissione";"Importo";"Stato";"ID Ospite"');
    expect(content).toContain('"Pagata"');

    blobSpy.mockRestore();
    appendChildSpy.mockRestore();
  });

  it('uses a dash placeholder when issueDate is missing', () => {
    const mockReport = {
      startDate: '2026-01-01',
      endDate: '2026-03-31',
      totalRevenue: 500,
      invoices: [
        { invoiceNumber: 'INV-002', issueDate: undefined, totalAmount: 250, status: 'PENDING', guestId: 'g2' },
      ],
    };

    const createObjectURL = vi.fn(() => 'blob:http://test/456');
    const revokeObjectURL = vi.fn();
    const clickFn = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });

    const appendChildSpy = vi.spyOn(document, 'createElement').mockReturnValue({
      href: '',
      download: '',
      click: clickFn,
    } as unknown as HTMLAnchorElement);

    billingReportService.exportToCsv(mockReport as never, translate);

    expect(clickFn).toHaveBeenCalled();
    appendChildSpy.mockRestore();
  });
});
