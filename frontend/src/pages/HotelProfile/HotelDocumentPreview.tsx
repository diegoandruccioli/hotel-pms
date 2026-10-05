import { useCallback, useState, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Card } from '../../components/m3';

interface HotelDocumentPreviewProps {
  hotelName: string;
  address: string;
  cap: string;
  comune: string;
  provincia: string;
  vatNumber: string;
  logoUrl: string;
}

/** Read-only sketch of the header that fiscal documents carry, fed live from the form. */
export const HotelDocumentPreview = memo(({
  hotelName, address, cap, comune, provincia, vatNumber, logoUrl,
}: HotelDocumentPreviewProps) => {
  const { t } = useTranslation('admin');
  // Remember which URL failed, so typing a new one gets a fresh attempt.
  const [failedLogoUrl, setFailedLogoUrl] = useState('');
  const handleLogoError = useCallback(() => setFailedLogoUrl(logoUrl), [logoUrl]);

  const locality = [cap, comune, provincia ? `(${provincia})` : ''].filter(Boolean).join(' ');

  return (
    <M3Card variant="solid" className="h-fit p-5">
      <section aria-labelledby="hotel-preview-heading" className="space-y-3">
        <h2 id="hotel-preview-heading" className="text-base font-semibold text-on-surface">
          {t('hotel_preview_title')}
        </h2>
        <p className="text-xs text-on-surface-variant">{t('hotel_preview_hint')}</p>
        <div className="space-y-1 rounded-shape-sm border border-outline-variant p-4 text-sm font-body">
          {logoUrl && failedLogoUrl !== logoUrl && (
            <img
              src={logoUrl}
              alt={t('hotel_logo_preview_alt')}
              className="mb-2 max-h-16 object-contain"
              onError={handleLogoError}
            />
          )}
          <p className="font-semibold text-on-surface">{hotelName || '—'}</p>
          {address && <p className="text-on-surface-variant">{address}</p>}
          {locality && <p className="text-on-surface-variant">{locality}</p>}
          {vatNumber && (
            <p className="text-on-surface-variant">
              {t('label_vat_number')}: {vatNumber}
            </p>
          )}
        </div>
      </section>
    </M3Card>
  );
});

HotelDocumentPreview.displayName = 'HotelDocumentPreview';
