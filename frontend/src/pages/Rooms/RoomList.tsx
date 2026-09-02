import { useState, useCallback, useMemo, memo } from 'react';
import { useLocation } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { RoomResponse } from '../../types';
import { M3Button } from '../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { M3LoadingState } from '../../components/m3';
import { M3ErrorState } from '../../components/m3';
import { M3TableEmptyRow } from '../../components/m3';
import { useRoomsList, useRoomTypes } from '../../hooks/queries';
import { queryKeys } from '../../lib';
import { getErrorMessage } from '../../utils';
import { RoomFormModal } from './RoomFormModal';

interface RoomListNavState {
  availableToday?: boolean;
}

const getStatusTone = (status: string) => {
  switch (status) {
    case 'CLEAN': return 'success' as const;
    case 'DIRTY': return 'warning' as const;
    case 'MAINTENANCE': return 'error' as const;
    default: return 'neutral' as const;
  }
};

const RoomRow = memo(({ room, onEdit, t }: {
  room: RoomResponse;
  onEdit: (r: RoomResponse) => void;
  t: (k: string) => string;
}) => {
  const handleEdit = useCallback(() => {
    onEdit(room);
  }, [onEdit, room]);

  return (
    <M3TableRow key={room.id}>
      <M3TableCell className="font-bold">{room.roomNumber}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{room.roomType.name}</M3TableCell>
      <M3TableCell>
        <M3StatusChip label={t(`room_status_${room.status.toLowerCase()}`)} tone={getStatusTone(room.status)} />
      </M3TableCell>
      <M3TableCell className="text-right">
        <M3TableActionLink onClick={handleEdit} className="lg:mr-4">
          {t('edit')}
        </M3TableActionLink>
      </M3TableCell>
    </M3TableRow>
  );
});

export const RoomList = memo(() => {
  const { t } = useTranslation('common');
  const location = useLocation();
  const queryClient = useQueryClient();
  const [availableOnly, setAvailableOnly] = useState(
    () => ((location.state as RoomListNavState | null)?.availableToday ?? false),
  );

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<RoomResponse | undefined>();

  const {
    data: roomsData,
    isLoading: roomsLoading,
    error: roomsError,
    refetch: refetchRooms,
  } = useRoomsList(availableOnly);
  const {
    data: roomTypesData,
    isLoading: roomTypesLoading,
    error: roomTypesError,
    refetch: refetchRoomTypes,
  } = useRoomTypes();
  const rooms = roomsData ?? [];
  const roomTypes = roomTypesData ?? [];
  const loading = roomsLoading || roomTypesLoading;
  const queryError = roomsError ?? roomTypesError;
  const error = queryError ? getErrorMessage(queryError, t('error_unexpected_fallback')) : null;

  const handleRetry = useCallback(() => {
    refetchRooms();
    refetchRoomTypes();
  }, [refetchRooms, refetchRoomTypes]);

  const toggleAvailableOnly = useCallback(() => {
    setAvailableOnly((prev) => !prev);
  }, []);

  const openAddModal = useCallback(() => {
    setEditingRoom(undefined);
    setIsModalOpen(true);
  }, []);

  const openEditModal = useCallback((room: RoomResponse) => {
    setEditingRoom(room);
    setIsModalOpen(true);
  }, []);

  const closeModal = useCallback(() => {
    setIsModalOpen(false);
  }, []);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    queryClient.invalidateQueries({ queryKey: queryKeys.rooms.all });
  }, [queryClient]);

  const headers = useMemo(() => [
    t('room_number_col'),
    t('room_type'),
    t('status'),
    t('actions')
  ], [t]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap justify-between items-center gap-3">
        <h2 className="text-xl font-display font-medium text-on-surface">{t('tab_rooms')}</h2>
        <div className="flex items-center gap-3">
          <button
            type="button"
            aria-pressed={availableOnly}
            onClick={toggleAvailableOnly}
            className={`px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors ${
              availableOnly
                ? 'bg-primary text-on-primary border-primary'
                : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline'
            }`}
          >
            {t('rooms_available_today_filter')}
          </button>
          <M3Button icon="add" onClick={openAddModal} disabled={roomTypes.length === 0}>
            {t('add_room')}
          </M3Button>
        </div>
      </div>

      {roomTypes.length === 0 && !loading && !error && (
        <div className="p-4 bg-tertiary-container text-on-tertiary-container rounded-shape-md mb-4 text-sm font-body">
          {t('error_loading_room_types')} ({t('add_room_type')} prima)
        </div>
      )}

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('failed_load_rooms')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3Table headers={headers}>
          {rooms.length === 0 ? (
            <M3TableEmptyRow
              colSpan={headers.length}
              message={availableOnly ? t('no_rooms_available_today') : t('no_rooms_found')}
            />
          ) : (
            rooms.map((room) => (
              <RoomRow key={room.id} room={room} onEdit={openEditModal} t={t} />
            ))
          )}
        </M3Table>
      )}

      {isModalOpen && (
        <RoomFormModal
          room={editingRoom}
          roomTypes={roomTypes}
          onClose={closeModal}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
});

RoomList.displayName = 'RoomList';
