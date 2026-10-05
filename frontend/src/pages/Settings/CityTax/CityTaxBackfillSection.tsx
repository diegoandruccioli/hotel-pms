import { useState, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useFormatters } from '../../../hooks';
import { stayService } from '../../../services';
import type { CityTaxBackfillResponse } from '../../../types';
import { M3Button } from '../../../components/m3';
import { M3Card } from '../../../components/m3';
import { M3ConfirmDialog } from '../../../components/m3';
import { M3EmptyState } from '../../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../../components/m3';
import { useToastStore } from '../../../store';
import { getErrorMessage } from '../../../utils';

/** Recovers tourist tax for stays that were never assessed because of a configuration gap (Parte 5.4).
 * Preview never writes or charges anything; only the explicit, confirmed step posts charges, and only
 * for stays whose invoice is still open — a closed/paid invoice is left untouched, never re-opened. */
const BackfillRow = memo(({ line, t }: {
  line: CityTaxBackfillResponse['lines'][number];
  t: (key: string) => string;
}) => {
  const { formatCurrency } = useFormatters();
  return (
  <M3TableRow>
    <M3TableCell>{line.checkInDate}</M3TableCell>
    <M3TableCell>{formatCurrency(line.amount)}</M3TableCell>
    <M3TableCell>
      {line.charged
        ? t('city_tax_backfill_status_charged')
        : line.skipReason
          ? t(`city_tax_backfill_skip_${line.skipReason.toLowerCase()}`)
          : t('city_tax_backfill_status_pending')}
    </M3TableCell>
  </M3TableRow>
  );
});
BackfillRow.displayName = 'BackfillRow';

export const CityTaxBackfillSection = () => {
  const { t } = useTranslation(['settings', 'common']);
  const { formatCurrency } = useFormatters();
  const addToast = useToastStore((s) => s.addToast);

  const [result, setResult] = useState<CityTaxBackfillResponse | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [askingConfirmation, setAskingConfirmation] = useState(false);

  const handlePreview = useCallback(async () => {
    setPreviewing(true);
    setConfirmed(false);
    try {
      const data = await stayService.previewCityTaxBackfill();
      setResult(data);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('city_tax_backfill_err_preview')), 'error');
    } finally {
      setPreviewing(false);
    }
  }, [addToast, t]);

  const openConfirmation = useCallback(() => setAskingConfirmation(true), []);
  const closeConfirmation = useCallback(() => setAskingConfirmation(false), []);

  const handleConfirm = useCallback(async () => {
    setConfirming(true);
    try {
      const data = await stayService.confirmCityTaxBackfill();
      setResult(data);
      setConfirmed(true);
      addToast(t('city_tax_backfill_success', { count: data.chargedCount }), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('city_tax_backfill_err_confirm')), 'error');
    } finally {
      setConfirming(false);
      setAskingConfirmation(false);
    }
  }, [addToast, t]);

  const tableHeaders = useMemo(() => [
    t('city_tax_backfill_col_checkin'),
    t('city_tax_backfill_col_amount'),
    t('city_tax_backfill_col_status'),
  ], [t]);

  const pendingCount = result ? result.lines.filter((l) => !l.charged && !l.skipReason).length : 0;
  const canConfirm = !!result && !confirmed && pendingCount > 0;

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <div>
        <h2 className="text-sm font-semibold text-on-surface">{t('city_tax_backfill_section_title')}</h2>
        <p className="text-xs text-on-surface-variant mt-0.5">{t('city_tax_backfill_section_desc')}</p>
      </div>

      <div className="flex gap-3">
        <M3Button variant="outlined" icon="search" onClick={handlePreview} loading={previewing}>
          {t('city_tax_backfill_action_preview')}
        </M3Button>
        {canConfirm && (
          <M3Button icon="check" onClick={openConfirmation} disabled={confirming}>
            {t('city_tax_backfill_action_confirm')}
          </M3Button>
        )}
      </div>

      {result && (
        result.lines.length === 0 ? (
          <M3EmptyState icon="task_alt" title={t('city_tax_backfill_none_found')} className="py-2" />
        ) : (
          <>
            <p className="text-sm font-medium text-on-surface">
              {t('city_tax_backfill_total', { total: formatCurrency(result.totalAmount) })}
            </p>
            <M3Table headers={tableHeaders}>
              {result.lines.map((line) => (
                <BackfillRow key={`${line.stayId}-${line.checkInDate}`} line={line} t={t} />
              ))}
            </M3Table>
          </>
        )
      )}

      {askingConfirmation && (
        <M3ConfirmDialog
          title={t('city_tax_backfill_confirm_title')}
          message={t('city_tax_backfill_confirm_message', { count: pendingCount, total: formatCurrency(result?.totalAmount ?? 0) })}
          confirmLabel={t('city_tax_backfill_confirm_yes')}
          onConfirm={handleConfirm}
          onCancel={closeConfirmation}
          loading={confirming}
        />
      )}
    </M3Card>
  );
};
