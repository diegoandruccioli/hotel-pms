import type { InvoiceResponse } from './billing.types';

export interface OwnerFinancialReportDto {
  startDate: string;
  endDate: string;
  totalRevenue: number;
  totalInvoices: number;
  paidInvoices: number;
  invoices: InvoiceResponse[];
}

/** Same totals as {@link OwnerFinancialReportDto}, without the per-invoice list. */
export interface OwnerFinancialSummaryDto {
  startDate: string;
  endDate: string;
  totalRevenue: number;
  totalInvoices: number;
  paidInvoices: number;
  /** Sum of ISSUED (unpaid) invoice amounts — owed but not yet collected. */
  pendingRevenue: number;
}

/** Time-bucket size for the KPI trend report — mirrors the backend's
 * `ReportGranularity` enum by name. */
export type ReportGranularity = 'DAY' | 'WEEK' | 'MONTH';

/** RevPAR/ADR/Occupancy for one time bucket (or, as `totals` on
 * {@link KpiReportDto}, the whole reporting period). */
export interface KpiPeriodDto {
  periodStart: string;
  totalRoomRevenue: number;
  occupiedRoomNights: number;
  availableRoomNights: number;
  /** Average daily rate: totalRoomRevenue / occupiedRoomNights. */
  adr: number;
  /** Revenue per available room: totalRoomRevenue / availableRoomNights. */
  revpar: number;
  /** occupiedRoomNights / availableRoomNights, as a fraction in [0, 1]. */
  occupancyRate: number;
}

export interface KpiReportDto {
  periods: KpiPeriodDto[];
  totals: KpiPeriodDto;
}
