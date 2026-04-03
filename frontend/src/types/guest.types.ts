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
  email: string;
  phone?: string;
  address?: string;
  city?: string;
  country?: string;
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
  identityDocuments?: IdentityDocumentResponseDTO[];
  createdAt: string;
  updatedAt: string;
  active: boolean;
}
