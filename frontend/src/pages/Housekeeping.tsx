import { useFormatters } from '../hooks';
import { useState, useCallback, memo, useMemo } from 'react';
import { useToastStore } from '../store';
import type { RoomResponse, RoomStatus } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3Checkbox } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage, cn, roomStatusTone, toneCardClasses, toneOutlineButtonClasses } from '../utils';
import { useRoomsList, useUpdateRoomStatus, useBulkUpdateRoomStatus } from '../hooks/queries';
import { useDaySheet } from '../hooks/queries';
import { HousekeepingWorksheetSection } from './Housekeeping/HousekeepingWorksheetSection';

const STATUS_KEYS: Record<RoomStatus, string> = {
  CLEAN: 'room_status_clean',
  DIRTY: 'room_status_dirty',
  MAINTENANCE: 'room_status_maintenance',
  OCCUPIED: 'room_status_occupied',
};

const ALL_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE'];
const EMPTY_ROOMS: RoomResponse[] = [];

const RoomCard = memo(({
  room,
  selected,
  onToggleSelected,
  onStatusChange,
}: {
  room: RoomResponse;
  selected: boolean;
  onToggleSelected: (id: string) => void;
  onStatusChange: (id: string, status: RoomStatus) => Promise<void>;
}) => {
  const { t } = useTranslation('common');
  const [updating, setUpdating] = useState<RoomStatus | null>(null);

  const { formatCurrency } = useFormatters();

  const handleStatusButton = useCallback(async (newStatus: RoomStatus) => {
    if (newStatus === room.status) return;
    setUpdating(newStatus);
    try {
      await onStatusChange(room.id, newStatus);
    } finally {
      setUpdating(null);
    }
  }, [room.status, room.id, onStatusChange]);

  const handleToggle = useCallback(() => {
    onToggleSelected(room.id);
  }, [onToggleSelected, room.id]);

  return (
    <div className={cn('rounded-shape-md border-2 p-4 flex flex-col gap-3 shadow-elevation-1 transition-all', toneCardClasses[roomStatusTone[room.status]])}>
      {/* flex-wrap: the checkbox + room-number block + status chip can outgrow a
          4-up grid column for the longer status labels (e.g. "Maintenance").
          Without wrapping, the flex-1 min-w-0 heading block was the only shrinkable
          item and got squeezed to zero width — the room number disappeared
          entirely instead of just re-flowing the chip onto its own line. */}
      <div className="flex items-start justify-between gap-2 flex-wrap">
        {room.status !== 'OCCUPIED' && (
          <M3Checkbox
            label={t('select_room', { number: room.roomNumber })}
            className="[&>label]:sr-only"
            checked={selected}
            onChange={handleToggle}
          />
        )}
        <div className="flex-1 min-w-16">
          <h3 className="text-lg font-display font-bold text-on-surface truncate">{t('room_number', { number: room.roomNumber })}</h3>
          <p className="text-xs font-body font-medium uppercase tracking-wide text-on-surface-variant truncate">{room.roomType?.name}</p>
        </div>
        <M3StatusChip label={t(STATUS_KEYS[room.status])} tone={roomStatusTone[room.status]} />
      </div>

      <p className="text-sm font-body text-on-surface-variant">{formatCurrency(room.roomType?.basePrice)} / {t('night')}</p>

      {room.status !== 'OCCUPIED' && (
        <div className="flex gap-2 flex-wrap mt-1">
          {ALL_STATUSES.filter((s) => s !== room.status).map((newStatus) => (
            <StatusButton
              key={newStatus}
              newStatus={newStatus}
              updating={updating}
              onClick={handleStatusButton}
              t={t}
            />
          ))}
        </div>
      )}
    </div>
  );
});

const StatusButton = memo(({ newStatus, updating, onClick, t }: {
  newStatus: RoomStatus;
  updating: RoomStatus | null;
  onClick: (s: RoomStatus) => void;
  t: (k: string) => string;
}) => {
  const handleClick = useCallback(() => {
    onClick(newStatus);
  }, [onClick, newStatus]);

  return (
    <button
      onClick={handleClick}
      disabled={updating !== null}
      className={cn(
        'flex-1 flex items-center justify-center min-h-10 text-xs font-medium font-body border rounded-shape-sm px-3 py-1.5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        toneOutlineButtonClasses[roomStatusTone[newStatus]]
      )}
    >
      {updating === newStatus ? (
        <span className="flex items-center justify-center gap-1">
          <MaterialIcon name="progress_activity" size={12} className="animate-spin" />
          {t('saving')}
        </span>
      ) : (
        `→ ${t(STATUS_KEYS[newStatus])}`
      )}
    </button>
  );
});

export const Housekeeping = memo(() => {
  const { t } = useTranslation('common');
  const [filter, setFilter] = useState<RoomStatus | 'ALL'>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const addToast = useToastStore((s) => s.addToast);

  const serverStatus = filter === 'ALL' ? undefined : filter;
  const { data: roomsData, isLoading: loading, error: queryError, refetch } = useRoomsList(false, serverStatus);
  const rooms = roomsData ?? EMPTY_ROOMS;
  const error = queryError ? getErrorMessage(queryError, t('failed_load_rooms')) : null;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  // Status-count badges reuse the day-sheet aggregate already fetched for the
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

  const handleFilterClick = useCallback((status: RoomStatus) => {
    setFilter(prev => prev === status ? 'ALL' : status);
    setSelectedIds(new Set());
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader
        icon="cleaning_services"
        title={t('nav_housekeeping')}
        subtitle={t('housekeeping_subtitle')}
        actions={
          <M3Button variant="outlined" icon="refresh" onClick={handleRetry}>
            {t('refresh')}
          </M3Button>
        }
      />

      <HousekeepingWorksheetSection />

      <div className="grid grid-cols-3 gap-4">
        {ALL_STATUSES.map((status) => (
          <FilterBadge
            key={status}
            status={status}
            active={filter === status}
            count={countByStatus(status)}
            onClick={handleFilterClick}
            t={t}
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
        <M3EmptyState
          icon="cleaning_services"
          title={t('no_rooms_found')}
          className="bg-surface rounded-shape-md shadow-elevation-1"
        />
      ) : (
        <>
          {selectableRooms.length > 0 && (
            <div className="flex flex-wrap items-center gap-3 rounded-shape-sm border border-outline-variant bg-surface-container-low px-4 py-3">
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
            </div>
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

const FilterBadge = memo(({ status, active, count, onClick, t }: {
  status: RoomStatus;
  active: boolean;
  count: number;
  onClick: (s: RoomStatus) => void;
  t: (k: string) => string;
}) => {
  const handleClick = useCallback(() => {
    onClick(status);
  }, [onClick, status]);

  return (
    <button
      onClick={handleClick}
      className={cn(
        'rounded-shape-md border-2 px-4 py-3 text-center transition-all shadow-elevation-1',
        active
          ? toneCardClasses[roomStatusTone[status]]
          : 'bg-surface border-outline-variant text-on-surface-variant hover:border-outline'
      )}
    >
      <p className="text-2xl font-display font-bold">{count}</p>
      <p className="text-xs font-body font-semibold uppercase tracking-wide">{t(STATUS_KEYS[status])}</p>
    </button>
  );
});
