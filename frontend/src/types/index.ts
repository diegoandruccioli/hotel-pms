// `DocumentType` is exported by both `billing.types` (FATTURA | RICEVUTA) and
// `guest.types` (PASSPORT | ID_CARD | DRIVERS_LICENSE | OTHER) — two distinct
// domain concepts that happen to share a name. Re-exported here under
// disambiguated aliases so the barrel doesn't hit an ambiguous-export error;
// importing directly from either `./billing.types` or `./guest.types` still
// gets the plain `DocumentType` name unchanged.
export type { DocumentType as BillingDocumentType } from './billing.types';
export type { DocumentType as GuestDocumentType } from './guest.types';

export * from './auth.types';
export type {
  InvoiceStatus,
  SdiStatus,
  PaymentMethod,
  ChargeType,
  ChargeResponse,
  PaymentRequest,
  PaymentResponse,
  InvoiceResponse,
  InvoiceSearchResult,
} from './billing.types';
export * from './daySheet.types';
export * from './fb.types';
export type {
  IdentityDocumentResponseDTO,
  GuestRequestDTO,
  GuestResponseDTO,
  GuestExportStaySummary,
  GuestExportInvoiceSummary,
  GuestDataExportResponse,
  GuestPrivacySettingsResponse,
  GuestPrivacySettingsRequest,
} from './guest.types';
export * from './inventory.types';
export * from './ownerReport.types';
export * from './page.types';
export * from './quotation.types';
export * from './reservation.types';
export * from './stay.types';
export * from './user.types';
