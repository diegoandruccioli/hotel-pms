import { describe, it, expect, vi, beforeEach } from 'vitest';
import { kpiReportService } from './kpiReportService';
import api from './api';
import type { KpiReportDto } from '../types';

vi.mock('./api');

describe('kpiReportService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches the KPI report for a date range and granularity', async () => {
    const mockReport: KpiReportDto = { points: [] } as unknown as KpiReportDto;
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockReport });

    const report = await kpiReportService.getKpiReport('2026-01-01', '2026-01-31', 'DAY');

    expect(api.get).toHaveBeenCalledWith('/api/v1/reports/kpi', {
      params: { startDate: '2026-01-01', endDate: '2026-01-31', granularity: 'DAY' },
    });
    expect(report).toEqual(mockReport);
  });
});
