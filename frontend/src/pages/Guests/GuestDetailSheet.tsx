import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Avatar, M3Button, M3SideSheet, M3StatusChip } from '../../components/m3';
import { useFormatters } from '../../hooks';
import { useGuestStayHistory } from '../../hooks/queries';
import { useAuthStore } from '../../store';
import { todayIsoDate } from '../../utils';
import type { GuestResponseDTO } from '../../types';
import type { IdentityDocumentResponseDTO } from '../../types/guest.types';
import { DetailSection, InvoiceHistorySection, StayHistorySection } from './GuestHistorySections';

interface GuestDetailSheetProps {
  guest: GuestResponseDTO;
  onClose: () => void;
  onEdit: (guest: GuestResponseDTO) => void;
}

interface FieldProps {
  label: string;
  children: React.ReactNode;
}

const Field = ({ label, children }: FieldProps) => (
  <div>
    <dt className="text-xs text-on-surface-variant">{label}</dt>
    <dd className="text-sm text-on-surface break-words">{children}</dd>
  </div>
);

const linkClass =
  'text-primary underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary';

const StaysCount = ({ guestId }: { guestId: string }) => {
  const { t } = useTranslation('guests');
  const { data } = useGuestStayHistory(guestId);
  return data?.length ? <M3StatusChip tone="info" label={t('stat_stays_count', { count: data.length })} /> : null;
};

const DocumentRow = ({ doc }: { doc: IdentityDocumentResponseDTO }) => {
  const { t } = useTranslation('guests');
  const { formatDate } = useFormatters();
  const expired = doc.expiryDate < todayIsoDate();
  return (
    <li className="flex items-center justify-between gap-3 py-2 text-sm">
      <div className="min-w-0">
        <p className="text-on-surface">
          {t(`doc_type_${doc.documentType}`, doc.documentType)} · {doc.documentNumber}
        </p>
        <p className="text-xs text-on-surface-variant">
          {t('label_document_expiry')} {formatDate(doc.expiryDate)}
        </p>
      </div>
      {expired && <M3StatusChip tone="error" label={t('status_document_expired')} />}
    </li>
  );
};

export const GuestDetailSheet = ({ guest, onClose, onEdit }: GuestDetailSheetProps) => {
  const { t } = useTranslation(['guests', 'common']);
  const { formatDate } = useFormatters();
  const role = useAuthStore((s) => s.user?.role);
  const canSeeInvoices = role === 'ADMIN' || role === 'OWNER';

  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);

  const fullName = `${guest.firstName} ${guest.lastName}`;
  const locality = [guest.cap, guest.comune, guest.provincia && `(${guest.provincia})`].filter(Boolean).join(' ');
  const address = [guest.address, locality, guest.country].filter(Boolean).join(', ');
  const documents = guest.identityDocuments ?? [];
  const hasFiscal = guest.fiscalCode || guest.vatNumber || guest.companyName;

  return (
    <M3SideSheet
      open
      title={t('detail_title')}
      onClose={onClose}
      footer={
        <div className="flex justify-end">
          <M3Button icon="edit" onClick={handleEdit}>{t('common:edit')}</M3Button>
        </div>
      }
    >
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <M3Avatar name={guest.firstName || guest.lastName} size="lg" aria-hidden="true" />
          <div className="min-w-0 space-y-1">
            <p className="text-lg font-semibold font-display text-on-surface break-words">{fullName}</p>
            <StaysCount guestId={guest.id} />
          </div>
        </div>

        <DetailSection heading={t('section_contacts')}>
          <dl className="space-y-2">
            {guest.email && (
              <Field label={t('common:email')}>
                <a className={linkClass} href={`mailto:${guest.email}`}>{guest.email}</a>
              </Field>
            )}
            {guest.phone && (
              <Field label={t('common:phone')}>
                <a className={linkClass} href={`tel:${guest.phone.replace(/\s/g, '')}`}>{guest.phone}</a>
              </Field>
            )}
            {address && <Field label={t('label_address')}>{address}</Field>}
            {guest.dateOfBirth && <Field label={t('label_birth_date')}>{formatDate(guest.dateOfBirth)}</Field>}
          </dl>
        </DetailSection>

        {hasFiscal && (
          <DetailSection heading={t('section_fiscal_data')}>
            <dl className="space-y-2">
              {guest.fiscalCode && <Field label={t('label_fiscal_code')}>{guest.fiscalCode}</Field>}
              {guest.vatNumber && <Field label={t('label_vat_number')}>{guest.vatNumber}</Field>}
              {guest.companyName && <Field label={t('label_company_name')}>{guest.companyName}</Field>}
            </dl>
          </DetailSection>
        )}

        <DetailSection heading={t('section_documents')}>
          {documents.length ? (
            <ul className="divide-y divide-outline-variant">
              {documents.map((doc) => <DocumentRow key={doc.id} doc={doc} />)}
            </ul>
          ) : (
            <p className="text-sm text-on-surface-variant">{t('msg_no_documents')}</p>
          )}
        </DetailSection>

        <StayHistorySection guestId={guest.id} />
        {canSeeInvoices && <InvoiceHistorySection guestId={guest.id} />}
      </div>
    </M3SideSheet>
  );
};
