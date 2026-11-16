import { useState, useCallback, useMemo, memo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { RoomResponse } from '../../types';
import { ListToolbar } from '../../components/ListToolbar';
import { M3Button, M3EmptyState } from '../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { M3FilterChip } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { M3LoadingState } from '../../components/m3';
import { M3ErrorState } from '../../components/m3';
import { useRoomsList, useRoomTypes } from '../../hooks/queries';
import { queryKeys } from '../../lib';
import { getErrorMessage, roomStatusTone } from '../../utils';
import { RoomFormModal } from './RoomFormModal';
import { HIDE_DETAIL_COLUMNS_ON_PHONE } from './phoneColumns';
import { readRoomListParams, roomListParamsToSearch } from './roomListParams';

interface RoomListProps {
  /** Takes the user to the room types tab (a room needs a type before it can be added). */
  onGoToTypes: () => void;
}

const ALL_TYPES = 'ALL';

const RoomRow = memo(({ room, onEdit, t }: {
  room: RoomResponse;
  onEdit: (r: RoomResponse) => void;
  t: (k: string) => string;
}) => {
  const handleEdit = useCallback(() => {
    onEdit(room);
  }, [onEdit, room]);

  const statusChip = (
    <M3StatusChip label={t(`room_status_${room.status.toLowerCase()}`)} tone={roomStatusTone[room.status]} />
  );

  return (
    <M3TableRow key={room.id}>
      <M3TableCell>
        <span className="font-bold">{room.roomNumber}</span>
        <div className="mt-1 flex flex-col items-start gap-1.5 text-on-surface-variant md:hidden">
          <span>{room.roomType.name}</span>
          {statusChip}
        </div>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{room.roomType.name}</M3TableCell>
      <M3TableCell>{statusChip}</M3TableCell>
      <M3TableCell className="text-right">
        <M3TableActionLink onClick={handleEdit} className="lg:mr-4">
          {t('edit')}
        </M3TableActionLink>
      </M3TableCell>
    </M3TableRow>
  );
});

export const RoomList = memo(({ onGoToTypes }: RoomListProps) => {
  const { t } = useTranslation('common');
  const queryClient = useQueryClient();
  const [urlParams, setUrlParams] = useSearchParams();
  const { typeId, availableToday: availableOnly } = readRoomListParams(urlParams);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<RoomResponse | undefined>();
  const [searchQuery, setSearchQuery] = useState('');

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
  const roomTypes = roomTypesData ?? [];
  // A type id that is not (or no longer) one of the loaded types counts as "all".
  const typeFilter = roomTypes.some((rt) => rt.id === typeId) ? typeId : ALL_TYPES;
  const loading = roomsLoading || roomTypesLoading;
  const queryError = roomsError ?? roomTypesError;
  const error = queryError ? getErrorMessage(queryError, t('error_unexpected_fallback')) : null;

  const handleRetry = useCallback(() => {
    refetchRooms();
    refetchRoomTypes();
  }, [refetchRooms, refetchRoomTypes]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  // Filters live in the URL (replace: no history entry per click); the tab is the rooms one by definition.
  const writeFilters = useCallback((patch: { typeId?: string; availableToday?: boolean }) => {
    setUrlParams(roomListParamsToSearch({
      tab: 'rooms', typeId: typeFilter === ALL_TYPES ? '' : typeFilter, availableToday: availableOnly, ...patch,
    }), { replace: true });
  }, [setUrlParams, typeFilter, availableOnly]);

  const setTypeFilter = useCallback((value: string) => {
    writeFilters({ typeId: value === ALL_TYPES ? '' : value });
  }, [writeFilters]);

  const toggleAvailableOnly = useCallback(() => {
    writeFilters({ availableToday: !availableOnly });
  }, [writeFilters, availableOnly]);

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

  // Search and type narrow the loaded list only (the backend has no room search, and the
  // list is capped at one page of 100, as before).
  const filtersActive = typeFilter !== ALL_TYPES || searchQuery.trim() !== '' || availableOnly;
  const visibleRooms = useMemo(() => {
    const needle = searchQuery.trim().toLowerCase();
    return (roomsData ?? []).filter(
      (r) => (typeFilter === ALL_TYPES || r.roomType.id === typeFilter)
        && (needle === '' || r.roomNumber.toLowerCase().includes(needle)),
    );
  }, [roomsData, typeFilter, searchQuery]);

  const handleShowAll = useCallback(() => {
    setSearchQuery('');
    writeFilters({ typeId: '', availableToday: false });
  }, [writeFilters]);

  const headers = useMemo(() => [
    t('room_number_col'),
    t('room_type'),
    t('status'),
    <span key="actions" className="sr-only">{t('actions')}</span>,
  ], [t]);

  return (
    <div className="space-y-4">
      <ListToolbar
        searchLabel={t('rooms_search_label')}
        searchPlaceholder={t('rooms_search_hint')}
        filtersLabel={t('rooms_filters_label')}
        searchValue={searchQuery}
        onSearchChange={handleSearchChange}
        trailing={
          <M3Button icon="add" onClick={openAddModal} disabled={roomTypes.length === 0}>
            {t('rooms_new_room')}
          </M3Button>
        }
      >
        <M3FilterChip
          value={ALL_TYPES}
          selected={typeFilter === ALL_TYPES}
          label={t('filter_all')}
          onValueSelect={setTypeFilter}
        />
        {roomTypes.map((rt) => (
          <M3FilterChip
            key={rt.id}
            value={rt.id}
            selected={typeFilter === rt.id}
            label={rt.name}
            onValueSelect={setTypeFilter}
          />
        ))}
        <M3FilterChip
          selected={availableOnly}
          onClick={toggleAvailableOnly}
          label={t('rooms_available_today_filter')}
        />
      </ListToolbar>

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('failed_load_rooms')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : visibleRooms.length === 0 ? (
        roomTypes.length === 0 ? (
          <M3EmptyState
            icon="category"
            title={t('rooms_no_types_title')}
            description={t('rooms_no_types_hint')}
            action={<M3Button variant="outlined" onClick={onGoToTypes}>{t('rooms_create_first_type')}</M3Button>}
          />
        ) : filtersActive ? (
          <M3EmptyState
            icon="meeting_room"
            title={availableOnly && !searchQuery.trim() && typeFilter === ALL_TYPES
              ? t('no_rooms_available_today')
              : t('no_rooms_match_filters')}
            action={<M3Button variant="outlined" onClick={handleShowAll}>{t('rooms_show_all')}</M3Button>}
          />
        ) : (
          <M3EmptyState icon="meeting_room" title={t('rooms_empty_title')} description={t('rooms_empty_hint')} />
        )
      ) : (
        <M3Table headers={headers} stickyEnd className={HIDE_DETAIL_COLUMNS_ON_PHONE}>
          {visibleRooms.map((room) => (
            <RoomRow key={room.id} room={room} onEdit={openEditModal} t={t} />
          ))}
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
