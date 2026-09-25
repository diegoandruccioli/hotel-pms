import { useState, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Card } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { housekeepingService } from '../../services';
import { useBusinessDate, useHousekeepingWorksheet } from '../../hooks/queries';
import { getErrorMessage } from '../../utils';
import type { HousekeepingTaskType } from '../../types';

const SUMMARY_TYPES: HousekeepingTaskType[] = [
  'DEPARTURE', 'STAYOVER', 'ARRIVAL_PREP', 'VACANT_DIRTY', 'MAINTENANCE',
];

const SUMMARY_KEYS: Record<HousekeepingTaskType, string> = {
  DEPARTURE: 'worksheet_summary_departure',
  STAYOVER: 'worksheet_summary_stayover',
  ARRIVAL_PREP: 'worksheet_summary_arrival_prep',
  VACANT_DIRTY: 'worksheet_summary_vacant_dirty',
  MAINTENANCE: 'worksheet_summary_maintenance',
};

export const HousekeepingWorksheetSection = memo(() => {
  const { t } = useTranslation('common');
  const { data: businessDate } = useBusinessDate();
  // Only ever holds a date the user explicitly picked. Until then, the
  // effective date below falls back to the server-resolved business date —
  // deliberately never a client-computed "today" (`new Date()`): that's
  // exactly the bug this feature exists to avoid (see BusinessDateResolver's
  // javadoc — before the hotel's cutoff hour, "today" by the calendar is
  // still yesterday's business date for the night shift). Derived directly
  // during render rather than synced via an effect, so a background refetch
  // of businessDate never clobbers a date the user already picked.
  const [userPickedDate, setUserPickedDate] = useState<string | undefined>(undefined);
  const date = userPickedDate ?? businessDate?.businessDate;

  const { data: worksheet, isLoading, error: queryError } = useHousekeepingWorksheet(date);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_worksheet')) : null;

  const handleDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setUserPickedDate(e.target.value);
  }, []);

  const handleDownload = useCallback(async () => {
    if (date) {
      await housekeepingService.downloadWorksheetPdf(date);
    }
  }, [date]);

  const dateMismatch = Boolean(businessDate && date && date !== businessDate.businessDate);

  return (
    <M3Card variant="outlined" className="p-5">
      <div className="flex items-center gap-2 mb-3">
        <MaterialIcon name="cleaning_services" size={20} className="text-primary" />
        <h2 className="text-sm font-display font-semibold text-on-surface">{t('worksheet_title')}</h2>
      </div>
      <p className="text-xs font-body text-on-surface-variant mb-4">{t('worksheet_desc')}</p>

      <div className="flex flex-col sm:flex-row items-end gap-3">
        <M3TextField
          className="flex-1"
          label={t('worksheet_date_label')}
          type="date"
          value={date ?? ''}
          onChange={handleDateChange}
        />
        <M3Button
          id="download-worksheet-btn"
          icon="download"
          disabled={!date}
          onClick={handleDownload}
        >
          {t('download_worksheet_pdf')}
        </M3Button>
      </div>

      {dateMismatch && (
        <div
          role="alert"
          className="mt-3 flex items-center gap-2 px-3 py-2 rounded-shape-sm bg-secondary-container text-on-secondary-container text-xs font-body"
        >
          <MaterialIcon name="warning" size={16} className="shrink-0" />
          {t('worksheet_date_mismatch_warning', { businessDate: businessDate?.businessDate })}
        </div>
      )}

      {error && <p className="mt-3 text-xs font-body text-error">{error}</p>}

      {worksheet && !isLoading && (
        <div className="mt-4">
          <div className="flex items-center gap-2 mb-2 flex-wrap">
            <M3StatusChip
              label={t(worksheet.provisional ? 'worksheet_provisional' : 'worksheet_definitive')}
              tone={worksheet.provisional ? 'warning' : 'success'}
            />
            {worksheet.provisional && (
              <span className="text-xs font-body text-on-surface-variant">{t('worksheet_provisional_hint')}</span>
            )}
          </div>
          <div className="flex flex-wrap gap-4">
            {SUMMARY_TYPES.map((type) => (
              <div key={type} className="flex items-baseline gap-1.5 text-xs font-body text-on-surface-variant">
                <span className="font-display font-bold text-on-surface">{worksheet.summary[type] ?? 0}</span>
                {t(SUMMARY_KEYS[type])}
              </div>
            ))}
          </div>
        </div>
      )}
    </M3Card>
  );
});

HousekeepingWorksheetSection.displayName = 'HousekeepingWorksheetSection';
