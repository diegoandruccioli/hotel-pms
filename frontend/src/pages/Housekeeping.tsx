import { useState, useEffect, useCallback, memo, useMemo } from 'react';
import { inventoryService } from '../services/inventoryService';
import { useToastStore } from '../store/toastStore';
import type { RoomResponse, RoomStatus } from '../types/inventory.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { useTranslation } from 'react-i18next';

const STATUS_KEYS: Record<RoomStatus, string> = {
  CLEAN: 'room_status_clean',
  DIRTY: 'room_status_dirty',
  MAINTENANCE: 'room_status_maintenance',
};

type StatusTone = 'success' | 'error' | 'warning';

const STATUS_CARD_STYLES: Record<RoomStatus, string> = {
  CLEAN: 'bg-tertiary-container/30 border-tertiary',
  DIRTY: 'bg-error-container/30 border-error',
  MAINTENANCE: 'bg-secondary-container/30 border-secondary',
};

const STATUS_TONES: Record<RoomStatus, StatusTone> = {
  CLEAN: 'success',
  DIRTY: 'error',
  MAINTENANCE: 'warning',
};

const STATUS_BUTTON_STYLES: Record<RoomStatus, string> = {
  CLEAN: 'border-tertiary text-tertiary hover:bg-tertiary-container',
  DIRTY: 'border-error text-error hover:bg-error-container',
  MAINTENANCE: 'border-secondary text-secondary hover:bg-secondary-container',
};

const ALL_STATUSES: RoomStatus[] = ['CLEAN', 'DIRTY', 'MAINTENANCE'];

const RoomCard = memo(({
  room,
  onStatusChange,
}: {
  room: RoomResponse;
  onStatusChange: (id: string, status: RoomStatus) => Promise<void>;
}) => {
  const { t, i18n } = useTranslation('common');
  const [updating, setUpdating] = useState<RoomStatus | null>(null);

  const formatCurrency = useCallback((amount: number | null | undefined) => {
    if (amount == null) return '—';
    return new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount);
  }, [i18n.language]);

  const handleStatusButton = useCallback(async (newStatus: RoomStatus) => {
    if (newStatus === room.status) return;
    setUpdating(newStatus);
    try {
      await onStatusChange(room.id, newStatus);
    } finally {
      setUpdating(null);
    }
  }, [room.status, room.id, onStatusChange]);

  return (
    <div className={`rounded-shape-md border-2 p-4 flex flex-col gap-3 shadow-elevation-1 transition-all ${STATUS_CARD_STYLES[room.status]}`}>
      <div className="flex items-start justify-between">
        <div>
          <h3 className="text-lg font-display font-bold text-on-surface">{t('room_number', { number: room.roomNumber })}</h3>
          <p className="text-xs font-body font-medium uppercase tracking-wide text-on-surface-variant">{room.roomType?.name}</p>
        </div>
        <M3StatusChip label={t(STATUS_KEYS[room.status])} tone={STATUS_TONES[room.status]} />
      </div>

      <p className="text-sm font-body text-on-surface-variant">{formatCurrency(room.roomType?.basePrice)} / {t('night')}</p>

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
      className={`flex-1 text-xs font-medium font-body border rounded-shape-sm px-3 py-1.5 transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${STATUS_BUTTON_STYLES[newStatus]}`}
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
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<RoomStatus | 'ALL'>('ALL');
  const addToast = useToastStore((s) => s.addToast);

  const loadRooms = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await inventoryService.getAllRooms();
      setRooms(data.content);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_load_rooms');
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    loadRooms();
  }, [loadRooms]);

  const handleStatusChange = useCallback(async (id: string, newStatus: RoomStatus) => {
    try {
      const updated = await inventoryService.updateRoomStatus(id, newStatus);
      setRooms((prev) => prev.map((r) => (r.id === id ? updated : r)));
      addToast(t('room_updated', { status: t(STATUS_KEYS[newStatus]) }), 'success');
    } catch {
      addToast(t('failed_update_room'), 'error');
    }
  }, [addToast, t]);

  const filteredRooms = useMemo(() => 
    filter === 'ALL' ? rooms : rooms.filter((r) => r.status === filter),
  [rooms, filter]);

  const countByStatus = useCallback((status: RoomStatus) => 
    rooms.filter((r) => r.status === status).length,
  [rooms]);

  const handleFilterClick = useCallback((status: RoomStatus) => {
    setFilter(prev => prev === status ? 'ALL' : status);
  }, []);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center gap-2">
            <MaterialIcon name="cleaning_services" className="text-primary" />
            {t('nav_housekeeping')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('housekeeping_subtitle')}</p>
        </div>
        <M3Button variant="outlined" icon="refresh" onClick={loadRooms}>
          {t('refresh')}
        </M3Button>
      </div>

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
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_rooms')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadRooms} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : filteredRooms.length === 0 ? (
        <div className="text-center py-16 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="cleaning_services" size={40} className="text-outline-variant mx-auto mb-3" />
          <p className="text-sm font-body text-on-surface-variant">{t('no_rooms_found')}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {filteredRooms.map((room) => (
            <RoomCard key={room.id} room={room} onStatusChange={handleStatusChange} />
          ))}
        </div>
      )}
    </div>
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
      className={`rounded-shape-md border-2 px-4 py-3 text-center transition-all shadow-elevation-1 ${
        active
          ? STATUS_CARD_STYLES[status]
          : 'bg-surface border-outline-variant text-on-surface-variant hover:border-outline'
      }`}
    >
      <p className="text-2xl font-display font-bold">{count}</p>
      <p className="text-xs font-body font-semibold uppercase tracking-wide">{t(STATUS_KEYS[status])}</p>
    </button>
  );
});
