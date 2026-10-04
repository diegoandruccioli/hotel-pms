import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3';
import { cn, roomStatusTone, toneDotClasses } from '../../utils';
import type { DaySheetResponse, RoomStatus } from '../../types';

const ROOM_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE', 'OCCUPIED'];

interface RoomStatusCardProps {
  counts: DaySheetResponse['roomStatusCounts'];
}

/**
 * Room overview: one stacked bar plus a text legend. The bar is decorative
 * (`aria-hidden`, colour alone) — every number and name is in the legend. Counts
 * only; the day-sheet doesn't carry the per-room list, Housekeeping does.
 */
export const RoomStatusCard = ({ counts }: RoomStatusCardProps) => {
  const { t } = useTranslation('common');

  const { rows, total } = useMemo(() => {
    const items = ROOM_STATUSES.map((status) => ({
      status,
      count: counts[status] ?? 0,
      dotClass: toneDotClasses[roomStatusTone[status]],
    }));
    const sum = items.reduce((acc, item) => acc + item.count, 0);
    return {
      total: sum,
      rows: items.map((item) => ({ ...item, style: { width: `${sum === 0 ? 0 : (item.count / sum) * 100}%` } })),
    };
  }, [counts]);

  return (
    <M3Card variant="solid" className="p-5">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <MaterialIcon name="grid_view" size={20} className="text-primary" />
          <h2 className="text-sm font-display font-semibold text-on-surface">{t('room_overview_title')}</h2>
        </div>
        <Link
          to="/housekeeping"
          aria-label={t('dashboard_view_all_rooms')}
          className="inline-flex items-center min-h-10 text-sm font-medium font-body text-primary hover:text-primary/80 focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-sm"
        >
          {t('view_all')}
        </Link>
      </div>
      <div
        aria-hidden="true"
        data-testid="room-status-bar"
        className={cn('flex h-3 overflow-hidden rounded-full', total === 0 && 'bg-surface-container-highest')}
      >
        {rows.map((row) => (
          <div key={row.status} data-status={row.status} className={row.dotClass} style={row.style} />
        ))}
      </div>
      <ul data-testid="room-status-summary" className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2">
        {rows.map((row) => (
          <li key={row.status} className="flex items-center gap-2 text-sm font-body text-on-surface">
            <span aria-hidden="true" className={cn('h-3 w-3 shrink-0 rounded-full', row.dotClass)} />
            <span className="min-w-0 flex-1 truncate text-on-surface-variant">{t(`room_status_${row.status.toLowerCase()}`)}</span>
            <span className="font-display font-bold tabular-nums">{row.count}</span>
          </li>
        ))}
      </ul>
    </M3Card>
  );
};
