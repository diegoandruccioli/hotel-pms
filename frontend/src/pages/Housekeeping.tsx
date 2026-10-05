import { useState, useCallback, memo, useMemo } from 'react';
import { useToastStore } from '../store';
import type { RoomResponse, RoomStatus } from '../types';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3Card } from '../components/m3';
import { M3Checkbox } from '../components/m3';
import { M3FilterChip } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage, cn, roomStatusTone, toneOutlineButtonClasses } from '../utils';
import { useRoomsList, useUpdateRoomStatus, useBulkUpdateRoomStatus } from '../hooks/queries';
import { useDaySheet } from '../hooks/queries';
import { HousekeepingWorksheetSection } from './Housekeeping/HousekeepingWorksheetSection';
import { RoomCard } from './Housekeeping/RoomCard';
import { STATUS_KEYS, ALL_STATUSES } from './Housekeeping/roomStatus';

type RoomFilter = RoomStatus | 'ALL';

const FILTERS: RoomFilter[] = ['ALL', ...ALL_STATUSES];
const EMPTY_ROOMS: RoomResponse[] = [];

export const Housekeeping = memo(() => {
  const { t } = useTranslation('common');
  const [filter, setFilter] = useState<RoomFilter>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const addToast = useToastStore((s) => s.addToast);

  const serverStatus = filter === 'ALL' ? undefined : filter;
  const { data: roomsData, isLoading: loading, error: queryError, refetch } = useRoomsList(false, serverStatus);
  const rooms = roomsData ?? EMPTY_ROOMS;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_rooms')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  // Status counts reuse the day-sheet aggregate already fetched for the
  // Dashboard instead of downloading every room a second time just to count them.
  const { data: daySheet } = useDaySheet();
  const countByStatus = useCallback(
    (status: RoomStatus) => daySheet?.roomStatusCounts[status] ?? 0,
    [daySheet],
  );

  const updateRoomStatus = useUpdateRoomStatus();
  const handleStatusChange = useCallback(async (id: string, newStatus: RoomStatus) => {
    try {
      await updateRoomStatus.mutateAsync({ id, status: newStatus });
      addToast(t('room_updated', { status: t(STATUS_KEYS[newStatus]) }), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('failed_update_room')), 'error');
    }
  }, [addToast, t, updateRoomStatus]);

  const bulkUpdateRoomStatus = useBulkUpdateRoomStatus();
  const handleBulkStatusChange = useCallback(async (newStatus: RoomStatus) => {
    const roomIds = Array.from(selectedIds);
    if (roomIds.length === 0) return;
    try {
      await bulkUpdateRoomStatus.mutateAsync({ roomIds, status: newStatus });
      addToast(t('rooms_updated', { count: roomIds.length, status: t(STATUS_KEYS[newStatus]) }), 'success');
      setSelectedIds(new Set());
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('failed_bulk_update_rooms')), 'error');
    }
  }, [addToast, t, bulkUpdateRoomStatus, selectedIds]);

  const handleToggleSelected = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }, []);

  const selectableRooms = useMemo(() => rooms.filter((r) => r.status !== 'OCCUPIED'), [rooms]);
  const allSelectableSelected = selectableRooms.length > 0 &&
    selectableRooms.every((r) => selectedIds.has(r.id));

  const handleToggleSelectAll = useCallback(() => {
    setSelectedIds(allSelectableSelected ? new Set() : new Set(selectableRooms.map((r) => r.id)));
  }, [allSelectableSelected, selectableRooms]);

  const handleFilterSelect = useCallback((next: RoomFilter) => {
    setFilter(next);
    setSelectedIds(new Set());
  }, []);

  const filterLabel = useCallback(
    (f: RoomFilter) => {
      if (f === 'ALL') return t('filter_all');
      // No count until the day-sheet arrives: "(0)" would read as "no rooms".
      return daySheet ? `${t(STATUS_KEYS[f])} (${countByStatus(f)})` : t(STATUS_KEYS[f]);
    },
    [t, daySheet, countByStatus],
  );

  const subtitle = daySheet
    ? t('housekeeping_status_summary', {
      dirty: countByStatus('DIRTY'),
      clean: countByStatus('CLEAN'),
      maintenance: countByStatus('MAINTENANCE'),
      occupied: countByStatus('OCCUPIED'),
    })
    : t('housekeeping_subtitle');

  return (
    <div className="space-y-6">
      <PageHeader
        icon="cleaning_services"
        title={t('nav_housekeeping')}
        subtitle={subtitle}
        actions={
          <M3Button variant="outlined" icon="refresh" onClick={handleRetry}>
            {t('refresh')}
          </M3Button>
        }
      />

      <HousekeepingWorksheetSection />

      <div role="group" aria-label={t('housekeeping_filter_label')} className="flex flex-wrap items-center gap-2">
        {FILTERS.map((f) => (
          <M3FilterChip
            key={f}
            label={filterLabel(f)}
            value={f}
            selected={filter === f}
            onValueSelect={handleFilterSelect}
          />
        ))}
      </div>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_rooms')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : rooms.length === 0 ? (
        <M3Card variant="solid">
          <M3EmptyState icon="cleaning_services" title={t('no_rooms_found')} />
        </M3Card>
      ) : (
        <>
          {selectableRooms.length > 0 && (
            <M3Card variant="solid" className="flex flex-wrap items-center gap-3 px-4 py-3">
              <M3Checkbox
                label={t('select_all_visible')}
                checked={allSelectableSelected}
                onChange={handleToggleSelectAll}
              />
              {selectedIds.size > 0 && (
                <>
                  <span className="text-sm font-body text-on-surface-variant">
                    {t('n_rooms_selected', { count: selectedIds.size })}
                  </span>
                  <span className="text-sm font-body text-on-surface-variant">{t('apply_status_to_selected')}:</span>
                  {ALL_STATUSES.map((status) => (
                    <BulkStatusButton
                      key={status}
                      status={status}
                      disabled={bulkUpdateRoomStatus.isPending}
                      onClick={handleBulkStatusChange}
                      t={t}
                    />
                  ))}
                </>
              )}
            </M3Card>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {rooms.map((room) => (
              <RoomCard
                key={room.id}
                room={room}
                selected={selectedIds.has(room.id)}
                onToggleSelected={handleToggleSelected}
                onStatusChange={handleStatusChange}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
});

const BulkStatusButton = memo(({ status, disabled, onClick, t }: {
  status: RoomStatus;
  disabled: boolean;
  onClick: (s: RoomStatus) => void;
  t: (k: string) => string;
}) => {
  const handleClick = useCallback(() => {
    onClick(status);
  }, [onClick, status]);

  return (
    <button
      onClick={handleClick}
      disabled={disabled}
      data-testid={`bulk-status-${status}`}
      className={cn(
        'flex items-center justify-center min-h-10 text-xs font-medium font-body border rounded-shape-sm px-3 py-1.5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        toneOutlineButtonClasses[roomStatusTone[status]]
      )}
    >
      {t(STATUS_KEYS[status])}
    </button>
  );
});
