import { useState, useEffect, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { quotationService } from '../../services/quotationService';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { getErrorMessage } from '../../utils';

interface Props {
  quotationId: string;
  onClose: () => void;
}

/**
 * Renders the quotation PDF inline via a blob: URL in an `<iframe>`. This is
 * a different consumption path from `quotationService.downloadPdf` (a hidden
 * iframe pointed straight at the endpoint, relying on `Content-Disposition:
 * attachment` to trigger a native download) — here the blob is meant to be
 * *displayed*, not saved, so the fetch+Blob approach billingService.ts
 * deliberately avoids for downloads is the right tool for preview.
 */
export const QuotationPdfPreviewDialog = memo(({ quotationId, onClose }: Props) => {
  const { t } = useTranslation(['quotations', 'common']);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    quotationService.getPdfBlob(quotationId)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(getErrorMessage(err, t('error_loading_pdf_preview')));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [quotationId, t]);

  const handleDownloadFallback = useCallback(() => {
    quotationService.downloadPdf(quotationId);
  }, [quotationId]);

  return (
    <M3Dialog
      open
      title={t('pdf_preview_title')}
      titleId="quotation-pdf-preview-title"
      onClose={onClose}
    >
      <div className="h-[70vh] flex flex-col">
        {loading ? (
          <div className="flex-1 flex items-center justify-center">
            <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
          </div>
        ) : error ? (
          <div className="flex-1 flex flex-col items-center justify-center gap-4 text-center px-6">
            <MaterialIcon name="error" size={32} className="text-error" />
            <p className="text-sm font-body text-on-surface-variant">{error}</p>
            <M3Button variant="outlined" icon="download" onClick={handleDownloadFallback}>
              {t('action_download_pdf')}
            </M3Button>
          </div>
        ) : (
          <iframe
            src={blobUrl ?? undefined}
            title={t('pdf_preview_title')}
            className="flex-1 w-full rounded-shape-sm border border-outline-variant"
          />
        )}
      </div>
    </M3Dialog>
  );
});

QuotationPdfPreviewDialog.displayName = 'QuotationPdfPreviewDialog';
