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
