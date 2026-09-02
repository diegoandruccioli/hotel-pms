import { useState, useEffect, useCallback, useMemo, useRef, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { format, eachDayOfInterval, startOfMonth, endOfMonth, addMonths, subMonths, isSameDay } from 'date-fns';
import { it, enUS } from 'date-fns/locale';
import { rateSeasonService } from '../../services/rateSeasonService';
import type { RateCalendarResponse } from '../../types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3';
import { M3Card } from '../../components/m3';
import { RateCalendarCell } from './RateCalendarCell';
import { RateBulkApplyDialog } from './RateBulkApplyDialog';
import { useAuthStore } from '../../store/authStore';
import { getErrorMessage } from '../../utils';

const SIDEBAR_WIDTH = 192;
const CELL_WIDTH = 100;
const ROW_HEIGHT = 64;
const SIDEBAR_STYLE = { width: SIDEBAR_WIDTH };
const ROW_STYLE = { height: ROW_HEIGHT };
const CELL_STYLE = { width: CELL_WIDTH };

/** Fixed accent palette for season identification — cycles if there are more
 * distinct seasons in view than colors (rare: a room type rarely has more
 * than a handful of seasons active within one visible month). */
const SEASON_COLOR_PALETTE = ['#6750A4', '#386A20', '#984061', '#006874', '#8C4A00', '#4A6572'];

interface Selection {
  anchorRow: number;
  anchorDay: number;
  row: number;
  day: number;
}

const LegendEntry = memo(({ name, color }: { name: string; color: string }) => {
  const dotStyle = useMemo(() => ({ backgroundColor: color }), [color]);
  return (
    <span className="flex items-center gap-1.5">
      <span className="w-2.5 h-2.5 rounded-full" style={dotStyle} aria-hidden="true" />
      {name}
    </span>
  );
});
LegendEntry.displayName = 'LegendEntry';

export const RateCalendar = () => {
  const { t, i18n } = useTranslation(['common']);
  const locale = i18n.language.startsWith('it') ? it : enUS;
  const role = useAuthStore((s) => s.user?.role);
  const canApplyPrice = role === 'ADMIN' || role === 'OWNER';

  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()));
  const [calendar, setCalendar] = useState<RateCalendarResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  const days = useMemo(
    () => eachDayOfInterval({ start: currentMonth, end: endOfMonth(currentMonth) }),
    [currentMonth],
  );

  const loadCalendar = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const from = format(currentMonth, 'yyyy-MM-dd');
      const to = format(endOfMonth(currentMonth), 'yyyy-MM-dd');
      const data = await rateSeasonService.getRateCalendar(from, to);
      setCalendar(data);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('error_loading_rate_calendar')));
    } finally {
      setLoading(false);
    }
  }, [currentMonth, t]);

  useEffect(() => {
    loadCalendar();
  }, [loadCalendar]);

  useEffect(() => {
    setSelection(null);
    if (scrollRef.current) scrollRef.current.scrollLeft = 0;
  }, [currentMonth]);

  // Ends a drag-selection even if the pointer is released outside any cell.
  useEffect(() => {
    const handlePointerUp = () => setIsDragging(false);
    window.addEventListener('pointerup', handlePointerUp);
    return () => window.removeEventListener('pointerup', handlePointerUp);
  }, []);

  const handlePrevMonth = useCallback(() => setCurrentMonth((d) => subMonths(d, 1)), []);
  const handleNextMonth = useCallback(() => setCurrentMonth((d) => addMonths(d, 1)), []);

  const handlePointerDown = useCallback((rowIndex: number, dayIndex: number) => {
    setIsDragging(true);
    setSelection({ anchorRow: rowIndex, anchorDay: dayIndex, row: rowIndex, day: dayIndex });
  }, []);

  const handlePointerEnter = useCallback((rowIndex: number, dayIndex: number) => {
    if (!isDragging) return;
    setSelection((prev) => (prev ? { ...prev, row: rowIndex, day: dayIndex } : prev));
  }, [isDragging]);

  const handleKeyboardSelect = useCallback((rowIndex: number, dayIndex: number, extend: boolean) => {
    setSelection((prev) => (extend && prev
      ? { ...prev, row: rowIndex, day: dayIndex }
      : { anchorRow: rowIndex, anchorDay: dayIndex, row: rowIndex, day: dayIndex }));
  }, []);

  const clearSelection = useCallback(() => setSelection(null), []);
  const openDialog = useCallback(() => setDialogOpen(true), []);
  const closeDialog = useCallback(() => setDialogOpen(false), []);

  const handleApplied = useCallback(() => {
    setSelection(null);
    loadCalendar();
  }, [loadCalendar]);

  const bounds = useMemo(() => {
    if (!selection) return null;
    return {
      rowMin: Math.min(selection.anchorRow, selection.row),
      rowMax: Math.max(selection.anchorRow, selection.row),
      dayMin: Math.min(selection.anchorDay, selection.day),
      dayMax: Math.max(selection.anchorDay, selection.day),
    };
  }, [selection]);

  const seasonColors = useMemo(() => {
    const map = new Map<string, string>();
    if (!calendar) return map;
    let index = 0;
    for (const row of calendar.rows) {
      for (const day of row.days) {
        if (day.rateSeasonId && !map.has(day.rateSeasonId)) {
          map.set(day.rateSeasonId, SEASON_COLOR_PALETTE[index % SEASON_COLOR_PALETTE.length]);
          index += 1;
        }
      }
    }
    return map;
  }, [calendar]);

  const legend = useMemo(() => {
    if (!calendar) return [];
    const seen = new Map<string, string>();
    for (const row of calendar.rows) {
      for (const day of row.days) {
        if (day.rateSeasonId && day.seasonName && !seen.has(day.rateSeasonId)) {
          seen.set(day.rateSeasonId, day.seasonName);
        }
      }
    }
    return Array.from(seen.entries()).map(([id, name]) => ({
      id, name, color: seasonColors.get(id) ?? SEASON_COLOR_PALETTE[0],
    }));
  }, [calendar, seasonColors]);

  const roomTypeOptions = useMemo(
    () => calendar?.rows.map((r) => ({ id: r.roomTypeId, name: r.roomTypeName })) ?? [],
    [calendar],
  );

  const dialogInitial = useMemo(() => {
    if (!bounds || !calendar) return undefined;
    const roomTypeIds = calendar.rows.slice(bounds.rowMin, bounds.rowMax + 1).map((r) => r.roomTypeId);
    const startDate = calendar.rows[0]?.days[bounds.dayMin]?.date;
    const endDate = calendar.rows[0]?.days[bounds.dayMax]?.date;
    return { roomTypeIds, startDate, endDate };
  }, [bounds, calendar]);

  const totalWidth = useMemo(() => SIDEBAR_WIDTH + days.length * CELL_WIDTH, [days.length]);
  const containerStyle = useMemo(() => ({ width: totalWidth }), [totalWidth]);
  const timelineStyle = useMemo(() => ({ width: days.length * CELL_WIDTH }), [days.length]);
  const selectedCellCount = bounds ? (bounds.rowMax - bounds.rowMin + 1) * (bounds.dayMax - bounds.dayMin + 1) : 0;

  const monthLabel = format(currentMonth, 'MMMM yyyy', { locale });

  return (
    <div className="space-y-4">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="payments" className="mr-2 text-primary" />
            {t('nav_rates')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('rate_calendar_subtitle')}</p>
        </div>
        {canApplyPrice && (
          <M3Button icon="add" onClick={openDialog}>{t('btn_apply_price')}</M3Button>
        )}
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <M3Button variant="outlined" icon="chevron_left" onClick={handlePrevMonth} aria-label={t('prev_month')} />
          <span className="text-sm font-medium font-body text-on-surface capitalize min-w-32 text-center">
            {monthLabel}
          </span>
          <M3Button variant="outlined" icon="chevron_right" onClick={handleNextMonth} aria-label={t('next_month')} />
        </div>

        {bounds && canApplyPrice && (
          <div className="flex items-center gap-3 px-3 py-1.5 bg-primary-container rounded-shape-full">
            <span className="text-sm font-body text-on-primary-container">
              {t('selection_summary', { count: selectedCellCount })}
            </span>
            <M3Button variant="text" onClick={openDialog}>{t('btn_apply_price')}</M3Button>
            <M3Button variant="text" onClick={clearSelection}>{t('btn_clear_selection')}</M3Button>
          </div>
        )}
      </div>

      {legend.length > 0 && (
        <div className="flex flex-wrap items-center gap-4 text-xs font-body text-on-surface-variant">
          {legend.map((entry) => (
            <LegendEntry key={entry.id} name={entry.name} color={entry.color} />
          ))}
          <span className="flex items-center gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full border border-outline-variant" aria-hidden="true" />
            {t('rate_calendar_legend_base_price')}
          </span>
        </div>
      )}

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_rate_calendar')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadCalendar} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : roomTypeOptions.length === 0 ? (
        <M3Card variant="outlined" className="p-8 text-center text-sm font-body text-on-surface-variant">
          {t('no_room_types_for_calendar')}
        </M3Card>
      ) : (
        <M3Card variant="outlined" className="overflow-hidden">
          <div className="overflow-auto" ref={scrollRef}>
            <div style={containerStyle}>
              <div className="sticky top-0 z-40 flex bg-surface-container-low border-b border-outline-variant" style={ROW_STYLE}>
                <div
                  className="sticky left-0 z-50 h-full bg-surface-container-low border-r border-outline-variant flex flex-col justify-center px-4 font-display font-bold text-sm text-primary shadow-elevation-1"
                  style={SIDEBAR_STYLE}
                >
                  {t('label_room_types')}
                </div>
                <div className="flex flex-1">
                  {days.map((day) => (
                    <div
                      key={day.toISOString()}
                      style={CELL_STYLE}
                      className={`shrink-0 border-r border-outline-variant flex flex-col items-center justify-center ${
                        isSameDay(day, new Date()) ? 'bg-primary-container text-on-primary-container' : ''
                      }`}
                    >
                      {/* text-on-surface-variant (not opacity-60 on inherited color) — axe
                          flagged the opacity-dimmed text at 4.35:1 against this cell's
                          bg-surface-container-low, below the WCAG AA 4.5:1 minimum. */}
                      <span className="text-[10px] uppercase font-bold tracking-wider text-on-surface-variant">
                        {format(day, 'EEE', { locale })}
                      </span>
                      <span className="text-lg font-display font-medium leading-none">{format(day, 'd')}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="flex relative items-start">
                <div
                  className="sticky left-0 z-30 shrink-0 bg-surface-container-low border-r border-outline-variant shadow-elevation-1"
                  style={SIDEBAR_STYLE}
                >
                  {calendar?.rows.map((row) => (
                    <div
                      key={row.roomTypeId}
                      style={ROW_STYLE}
                      className="border-b border-outline-variant flex flex-col justify-center px-4 bg-surface-container-low"
                    >
                      <span className="font-display font-bold text-on-surface truncate">{row.roomTypeName}</span>
                      <span className="text-xs text-on-surface-variant">€ {row.basePrice.toFixed(2)}</span>
                    </div>
                  ))}
                </div>

                <div className="flex-1" style={timelineStyle}>
                  {calendar?.rows.map((row, rowIndex) => (
                    <div key={row.roomTypeId} className="flex" style={ROW_STYLE}>
                      {row.days.map((day, dayIndex) => (
                        <RateCalendarCell
                          key={day.date}
                          day={day}
                          rowIndex={rowIndex}
                          dayIndex={dayIndex}
                          isSelected={!!bounds
                            && rowIndex >= bounds.rowMin && rowIndex <= bounds.rowMax
                            && dayIndex >= bounds.dayMin && dayIndex <= bounds.dayMax}
                          isToday={isSameDay(new Date(day.date), new Date())}
                          seasonColor={day.rateSeasonId ? seasonColors.get(day.rateSeasonId) : undefined}
                          ariaLabel={`${row.roomTypeName}, ${format(new Date(day.date), 'd MMMM', { locale })}, € ${day.price.toFixed(2)}${day.seasonName ? ` — ${day.seasonName}` : ''}`}
                          onPointerDown={canApplyPrice ? handlePointerDown : noop}
                          onPointerEnter={canApplyPrice ? handlePointerEnter : noop}
                          onKeyboardSelect={canApplyPrice ? handleKeyboardSelect : noop}
                        />
                      ))}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </M3Card>
      )}

      {dialogOpen && (
        <RateBulkApplyDialog
          roomTypes={roomTypeOptions}
          initialRoomTypeIds={dialogInitial?.roomTypeIds}
          initialStartDate={dialogInitial?.startDate}
          initialEndDate={dialogInitial?.endDate}
          onClose={closeDialog}
          onApplied={handleApplied}
        />
      )}
    </div>
  );
};

function noop() {
  // Read-only for roles that cannot apply prices — selection handlers are disabled.
}
