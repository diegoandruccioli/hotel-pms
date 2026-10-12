import { useState, useCallback, memo, useMemo } from 'react';
import { useToastStore } from '../store';
import type { RoomResponse, RoomStatus } from '../types';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3Card } from '../components/m3';
import { M3Checkbox } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage, cn, roomStatusTone, toneOutlineButtonClasses } from '../utils';
import { useRoomsList, useUpdateRoomStatus, useBulkUpdateRoomStatus } from '../hooks/queries';
import { useBusinessDate, useDaySheet, useHousekeepingWorksheet } from '../hooks/queries';
import { HousekeepingWorksheetSection } from './Housekeeping/HousekeepingWorksheetSection';
import { HousekeepingBoard } from './Housekeeping/HousekeepingBoard';
import { priorityRoomNumbers } from './Housekeeping/housekeepingPriority';
import { STATUS_KEYS, ALL_STATUSES } from './Housekeeping/roomStatus';

const EMPTY_ROOMS: RoomResponse[] = [];
const NO_PRIORITY_ROOMS: ReadonlySet<string> = new Set();

export const Housekeeping = memo(() => {
  const { t } = useTranslation('common');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const addToast = useToastStore((s) => s.addToast);

  const { data: roomsData, isLoading: loading, error: queryError, refetch } = useRoomsList(false);
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
  // Rooms whose update is in flight (the move is not optimistic: the card stays put until the
  // server answers), so a second drop or button click cannot race the first.
  const [busyIds, setBusyIds] = useState<ReadonlySet<string>>(new Set());
  const [focusRoomId, setFocusRoomId] = useState<string | null>(null);
  const handleFocusHandled = useCallback(() => setFocusRoomId(null), []);

  const handleStatusChange = useCallback(async (id: string, newStatus: RoomStatus) => {
    setBusyIds((prev) => new Set(prev).add(id));
    try {
      await updateRoomStatus.mutateAsync({ id, status: newStatus });
      addToast(t('room_updated', { status: t(STATUS_KEYS[newStatus]) }), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('failed_update_room')), 'error');
    } finally {
      setBusyIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  }, [addToast, t, updateRoomStatus]);

  // The card's own buttons: the moved card remounts in another column, so keyboard focus is handed back.
  const handleCardStatusChange = useCallback(async (id: string, newStatus: RoomStatus) => {
    await handleStatusChange(id, newStatus);
    setFocusRoomId(id);
  }, [handleStatusChange]);

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

  // Priority chips come from the worksheet of the hotel's current business date (the same
  // query the worksheet section starts from); without it the cards simply show no chip.
  const { data: businessDate } = useBusinessDate();
  const { data: worksheet } = useHousekeepingWorksheet(businessDate?.businessDate);
  const priorityRooms = useMemo(
    () => (worksheet ? priorityRoomNumbers(worksheet.rows) : NO_PRIORITY_ROOMS),
    [worksheet],
  );

  const handleDropRoom = useCallback((roomId: string, status: RoomStatus) => {
    const room = rooms.find((r) => r.id === roomId);
    if (!room || room.status === status || room.status === 'OCCUPIED' || busyIds.has(roomId)) return;
    void handleStatusChange(roomId, status);
  }, [rooms, busyIds, handleStatusChange]);

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

          <p className="text-sm font-body text-on-surface-variant">{t('housekeeping_drag_hint')}</p>
          <HousekeepingBoard
            rooms={rooms}
            selectedIds={selectedIds}
            priorityRooms={priorityRooms}
            busyIds={busyIds}
            focusRoomId={focusRoomId}
            onFocusHandled={handleFocusHandled}
            onToggleSelected={handleToggleSelected}
            onStatusChange={handleCardStatusChange}
            onDropRoom={handleDropRoom}
          />
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
