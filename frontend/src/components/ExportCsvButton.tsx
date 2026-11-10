import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Button } from './m3/M3Button';

interface ExportCsvButtonProps {
  /** Starts the download. Must handle its own errors (toast): a rejection is not caught here. */
  onExport: () => Promise<void>;
  className?: string;
}

/** The one "Export CSV" button for list and report pages. Outlined, so it stays a secondary action
 * next to the page's filled primary one; disabled with a spinner while the export runs. */
export const ExportCsvButton = ({ onExport, className }: ExportCsvButtonProps) => {
  const { t } = useTranslation();
  const [busy, setBusy] = useState(false);

  const handleClick = useCallback(async () => {
    setBusy(true);
    try {
      await onExport();
    } finally {
      setBusy(false);
    }
  }, [onExport]);

  return (
    <M3Button
      type="button"
      variant="outlined"
      icon="download"
      loading={busy}
      onClick={handleClick}
      className={className}
    >
      {t('export_csv')}
    </M3Button>
  );
};
