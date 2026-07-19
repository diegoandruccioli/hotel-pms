export type DocumentType = 'PASSPORT' | 'ID_CARD' | 'DRIVERS_LICENSE' | 'OTHER';



export interface IdentityDocumentResponseDTO {
  id: string; // UUID
  documentType: DocumentType;
  documentNumber: string;
  issueDate: string;
  expiryDate: string;
  issuingCountry?: string;
  createdAt: string; // LocalDateTime mapped to ISO string
  updatedAt: string;
  active: boolean;
}

export interface GuestRequestDTO {
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  address?: string;
  city?: string;
  country?: string;
  fiscalCode?: string;
  vatNumber?: string;
  companyName?: string;
  sdiCode?: string;
  pecEmail?: string;
  /** CAP — Italian 5-digit postal code. Required only to use this guest as a FatturaPA cessionario. */
  cap?: string;
  /** Comune — municipality name, validated together with provincia. */
  comune?: string;
  /** Provincia — 2-letter province code, e.g. "RM". */
  provincia?: string;
}

export interface GuestResponseDTO {
  id: string; // UUID
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  country?: string;
  fiscalCode?: string;
  vatNumber?: string;
  companyName?: string;
  sdiCode?: string;
  pecEmail?: string;
  cap?: string;
  comune?: string;
  provincia?: string;
  identityDocuments?: IdentityDocumentResponseDTO[];
  createdAt: string;
  updatedAt: string;
  active: boolean;
}
