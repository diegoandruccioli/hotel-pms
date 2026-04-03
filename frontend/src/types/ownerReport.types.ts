import type { InvoiceResponse } from './billing.types';

export interface OwnerFinancialReportDto {
  startDate: string;
  endDate: string;
  totalRevenue: number;
  totalInvoices: number;
  paidInvoices: number;
  invoices: InvoiceResponse[];
}
