import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryService } from '../../services/inventoryService';
import type { RoomResponse, RoomTypeResponse } from '../../types/inventory.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import { useToastStore } from '../../store/toastStore';
import { RoomFormModal } from './RoomFormModal';

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
        <button onClick={handleEdit} className="text-primary hover:text-primary/80 font-medium text-sm lg:mr-4">
          {t('edit')}
        </button>
      </M3TableCell>
    </M3TableRow>
  );
});

export const RoomList = memo(() => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [roomTypes, setRoomTypes] = useState<RoomTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<RoomResponse | undefined>();

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      // Fetch both rooms and room types in parallel
      const [roomsData, typesData] = await Promise.all([
        inventoryService.getAllRooms(),
        inventoryService.getAllRoomTypes(),
      ]);
      setRooms(roomsData.content);
      setRoomTypes(typesData);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = e.response?.data?.detail || e.message || t('failed_load_rooms');
      setError(errorMsg);
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [addToast, t]);

  useEffect(() => {
    loadData();
  }, [loadData]);

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
    loadData();
  }, [loadData]);

  const headers = useMemo(() => [
    t('room_number'), 
    t('room_type'), 
    t('status'), 
    t('actions')
  ], [t]);

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-display font-medium text-on-surface">{t('tab_rooms')}</h2>
        <M3Button icon="add" onClick={openAddModal} disabled={roomTypes.length === 0}>
          {t('add_room')}
        </M3Button>
      </div>

      {roomTypes.length === 0 && !loading && !error && (
        <div className="p-4 bg-tertiary-container text-on-tertiary-container rounded-shape-md mb-4 text-sm font-body">
          {t('error_loading_room_types')} ({t('add_room_type')} prima)
        </div>
      )}

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1 border border-outline-variant/30">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('failed_load_rooms')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadData} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={headers}>
          {rooms.length === 0 ? (
            <tr><td colSpan={4} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_rooms_found')}</td></tr>
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
