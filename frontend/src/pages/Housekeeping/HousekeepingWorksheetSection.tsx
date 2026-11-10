import { useCallback, useId, useMemo, useState, memo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { M3Card } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { Alert } from '../../components/Alert';
import { housekeepingService } from '../../services';
import { useBusinessDate, useHousekeepingWorksheet } from '../../hooks/queries';
import { getErrorMessage } from '../../utils';
import type { HousekeepingRow, HousekeepingTaskType } from '../../types';

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

/** The `?date=` value if it is a real calendar day (YYYY-MM-DD); anything else is ignored. */
function parseDateParam(raw: string | null): string | undefined {
  if (!raw || !/^\d{4}-\d{2}-\d{2}$/.test(raw)) return undefined;
  const parsed = new Date(`${raw}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === raw ? raw : undefined;
}

interface HousekeepingWorksheetSectionProps {
  /** Called with the room number when a row's number is clicked (the page scrolls to its card). */
  onRoomSelect: (roomNumber: string) => void;
}

export const HousekeepingWorksheetSection = memo(({ onRoomSelect }: HousekeepingWorksheetSectionProps) => {
  const { t } = useTranslation('common');
  const { data: businessDate } = useBusinessDate();
  // The picked date lives in `?date=` so the URL reopens the same sheet. Until a valid one is
  // there, the effective date falls back to the server-resolved business date — deliberately
  // never a client-computed "today" (`new Date()`): that's exactly the bug this feature exists
  // to avoid (see BusinessDateResolver's javadoc — before the hotel's cutoff hour, "today" by
  // the calendar is still yesterday's business date for the night shift). Derived during render
  // rather than synced via an effect, so a background refetch of businessDate never clobbers a
  // date the user already picked.
  const [searchParams, setSearchParams] = useSearchParams();
  const date = parseDateParam(searchParams.get('date')) ?? businessDate?.businessDate;

  const { data: worksheet, isLoading, error: queryError } = useHousekeepingWorksheet(date);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_worksheet')) : null;

  const setDateParam = useCallback((value: string | null) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      if (value) next.set('date', value); else next.delete('date');
      return next;
    }, { replace: true });
  }, [setSearchParams]);

  const handleDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setDateParam(e.target.value);
  }, [setDateParam]);

  const handleBackToCurrentDate = useCallback(() => setDateParam(null), [setDateParam]);

  const rowsByType = useMemo(() => groupRowsByType(worksheet?.rows ?? []), [worksheet]);

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

      <div className="flex flex-col sm:flex-row sm:items-end gap-3">
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
        <Alert tone="warning" compact role="alert" className="mt-3">
          <span>{t('worksheet_date_mismatch_warning', { businessDate: businessDate?.businessDate })}</span>{' '}
          <button
            type="button"
            onClick={handleBackToCurrentDate}
            className="inline-flex min-h-11 sm:pointer-fine:min-h-10 items-center font-medium underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
          >
            {t('worksheet_back_to_current_date')}
          </button>
        </Alert>
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
          <WorksheetRows rowsByType={rowsByType} onRoomSelect={onRoomSelect} />
        </div>
      )}
    </M3Card>
  );
});

HousekeepingWorksheetSection.displayName = 'HousekeepingWorksheetSection';

function groupRowsByType(rows: readonly HousekeepingRow[]) {
  return SUMMARY_TYPES
    .map((type) => ({ type, rows: rows.filter((r) => r.taskType === type) }))
    .filter((group) => group.rows.length > 0);
}

/**
 * The sheet's rooms grouped by task type: the room number is the thing to find (big, bold),
 * type and guest count are context (muted). No guest name — this mirrors the printed sheet.
 */
const WorksheetRows = memo(({ rowsByType, onRoomSelect }: {
  rowsByType: ReturnType<typeof groupRowsByType>;
  onRoomSelect: (roomNumber: string) => void;
}) => {
  const { t } = useTranslation('common');
  // Closed by default: the counts above already say what is on the sheet, and a long list would
  // push the board below the fold. A view preference, not content, so it stays out of the URL.
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const handleToggle = useCallback(() => setOpen((prev) => !prev), []);

  if (rowsByType.length === 0) {
    return <p className="mt-4 text-sm font-body text-on-surface-variant">{t('worksheet_empty')}</p>;
  }

  const total = rowsByType.reduce((sum, group) => sum + group.rows.length, 0);

  return (
    <div className="mt-4">
      <button
        type="button"
        onClick={handleToggle}
        aria-expanded={open}
        aria-controls={panelId}
        className="inline-flex min-h-11 sm:pointer-fine:min-h-10 items-center gap-1 rounded-sm text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary"
      >
        <MaterialIcon name={open ? 'expand_less' : 'expand_more'} size={20} />
        {open ? t('worksheet_rows_hide') : t('worksheet_rows_show', { count: total })}
      </button>
      <div id={panelId} hidden={!open} className="mt-2 space-y-4">
        <h3 className="text-sm font-display font-semibold text-on-surface">{t('worksheet_rows_title')}</h3>
        {rowsByType.map(({ type, rows }) => (
          <section key={type} aria-labelledby={`worksheet-group-${type}`}>
            <h4 id={`worksheet-group-${type}`} className="mb-1 text-xs font-medium font-body uppercase tracking-wide text-on-surface-variant">
              {t(SUMMARY_KEYS[type])} · {rows.length}
            </h4>
            <ul className="grid grid-cols-1 gap-x-6 sm:grid-cols-2 xl:grid-cols-3">
              {rows.map((row) => (
                <WorksheetRowItem key={row.roomNumber} row={row} onRoomSelect={onRoomSelect} />
              ))}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
});

WorksheetRows.displayName = 'WorksheetRows';

const WorksheetRowItem = memo(({ row, onRoomSelect }: {
  row: HousekeepingRow;
  onRoomSelect: (roomNumber: string) => void;
}) => {
  const { t } = useTranslation('common');
  const handleClick = useCallback(() => onRoomSelect(row.roomNumber), [onRoomSelect, row.roomNumber]);

  return (
    <li className="flex min-h-11 sm:pointer-fine:min-h-10 flex-wrap items-center gap-x-3 gap-y-1 py-1">
      <button
        type="button"
        onClick={handleClick}
        aria-label={t('worksheet_go_to_room', { number: row.roomNumber })}
        className="min-w-12 rounded-sm text-left text-lg font-display font-bold text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary"
      >
        {row.roomNumber}
      </button>
      <span className="text-xs font-body text-on-surface-variant">
        {row.roomTypeName}{row.pax > 0 && ` · ${t('worksheet_row_pax', { count: row.pax })}`}
      </span>
      {row.turnover && <M3StatusChip label={t('worksheet_turnover')} tone="warning" />}
    </li>
  );
});

WorksheetRowItem.displayName = 'WorksheetRowItem';
