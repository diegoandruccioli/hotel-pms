import api from './api';
import type { KpiReportDto, ReportGranularity } from '../types/ownerReport.types';

const KPI_REPORT_PATH = '/api/v1/reports/kpi';

export const kpiReportService = {
  getKpiReport: async (
    startDate: string,
    endDate: string,
    granularity: ReportGranularity
  ): Promise<KpiReportDto> => {
    const response = await api.get<KpiReportDto>(KPI_REPORT_PATH, {
      params: { startDate, endDate, granularity },
    });
    return response.data;
  },
};
