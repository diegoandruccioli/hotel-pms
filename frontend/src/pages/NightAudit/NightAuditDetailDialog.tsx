import { useTranslation } from 'react-i18next';
import type { NightAuditRunResponse } from '../../types';
import { useFormatters } from '../../hooks';
import { M3Button, M3Dialog, M3StatusChip } from '../../components/m3';
import { nightAuditStatusTone } from '../../utils';
import { getStatusLabel } from './nightAuditUtils';

interface NightAuditDetailDialogProps {
  run: NightAuditRunResponse;
  onClose: () => void;
}

export const NightAuditDetailDialog = ({ run, onClose }: NightAuditDetailDialogProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency, formatDate } = useFormatters();

  return (
    <M3Dialog
      open
      title={t('night_audit_detail_title', { date: formatDate(run.businessDate) })}
      titleId="night-audit-detail-dialog"
      onClose={onClose}
    >
      <div className="space-y-3 text-sm font-body text-on-surface">
        <div className="flex justify-between">
          <span className="text-on-surface-variant">{t('status')}</span>
          <M3StatusChip label={getStatusLabel(run.status, t)} tone={nightAuditStatusTone[run.status]} />
        </div>
        {run.status === 'FAILED' && run.failureReason && (
          <p className="text-error">{run.failureReason}</p>
        )}
        {run.status === 'COMPLETED' && (
          <>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">{t('night_audit_guests_in_house')}</span>
              <span>{run.guestsInHouse}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">{t('night_audit_current_stays')}</span>
              <span>{run.currentStays}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">{t('night_audit_available_rooms')}</span>
              <span>{run.availableRooms}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-on-surface-variant">{t('night_audit_no_shows_marked')}</span>
              <span>{run.noShowsMarked}</span>
            </div>
            <hr className="border-outline-variant" />
            <p className="font-medium">
              {t('night_audit_cash_total')}
              {run.cashSummaryDegraded && (
                <span className="ml-2 text-secondary text-xs">{t('night_audit_cash_degraded')}</span>
              )}
            </p>
            {run.cashByMethod.length === 0 ? (
              <p className="text-on-surface-variant">{t('night_audit_no_cash_activity')}</p>
            ) : (
              <ul className="space-y-1">
                {run.cashByMethod.map((line) => (
                  <li key={line.paymentMethod} className="flex justify-between">
                    <span className="text-on-surface-variant">{line.paymentMethod}</span>
                    <span>{formatCurrency(line.total)}</span>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </div>
      <div className="flex justify-end pt-4">
        <M3Button type="button" variant="outlined" onClick={onClose}>
          {t('close')}
        </M3Button>
      </div>
    </M3Dialog>
  );
};
