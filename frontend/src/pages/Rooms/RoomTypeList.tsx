import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryService } from '../../services/inventoryService';
import type { RoomTypeResponse } from '../../types/inventory.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { useToastStore } from '../../store/toastStore';
import { RoomTypeFormModal } from './RoomTypeFormModal';

const RoomTypeRow = memo(({ rt, onEdit, t }: {
  rt: RoomTypeResponse;
  onEdit: (rt: RoomTypeResponse) => void;
  t: (k: string) => string;
}) => {
  const handleEdit = useCallback(() => {
    onEdit(rt);
  }, [onEdit, rt]);

  return (
    <M3TableRow key={rt.id}>
      <M3TableCell className="font-medium">{rt.name}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">{rt.maxOccupancy}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">€ {rt.basePrice.toFixed(2)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant max-w-xs truncate" title={rt.description}>{rt.description || '-'}</M3TableCell>
      <M3TableCell className="text-right">
        <button onClick={handleEdit} className="text-primary hover:text-primary/80 font-medium text-sm lg:mr-4">
          {t('edit')}
        </button>
      </M3TableCell>
    </M3TableRow>
  );
});

export const RoomTypeList = memo(() => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [types, setTypes] = useState<RoomTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<RoomTypeResponse | undefined>();

  const loadTypes = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await inventoryService.getAllRoomTypes();
      setTypes(data);
    } catch (err: unknown) {
      const e = err as {response?: {data?: {detail?: string}}, message?: string};
      const errorMsg = e.response?.data?.detail || e.message || t('error_loading_room_types');
      setError(errorMsg);
      addToast(errorMsg, 'error');
    } finally {
      setLoading(false);
    }
  }, [addToast, t]);

  useEffect(() => {
    loadTypes();
  }, [loadTypes]);

  const openAddModal = useCallback(() => {
    setEditingType(undefined);
    setIsModalOpen(true);
  }, []);

  const openEditModal = useCallback((roomType: RoomTypeResponse) => {
    setEditingType(roomType);
    setIsModalOpen(true);
  }, []);

  const closeModal = useCallback(() => {
    setIsModalOpen(false);
  }, []);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    loadTypes();
  }, [loadTypes]);

  const headers = useMemo(() => [
    t('name'), 
    t('max_occupancy'), 
    t('base_price'), 
    t('description'), 
    t('actions')
  ], [t]);

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-display font-medium text-on-surface">{t('tab_room_types')}</h2>
        <M3Button icon="add" onClick={openAddModal}>{t('add_room_type')}</M3Button>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1 border border-outline-variant/30">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_room_types')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadTypes} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={headers}>
          {types.length === 0 ? (
            <tr><td colSpan={5} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_rooms_found')}</td></tr>
          ) : (
            types.map((rt) => (
              <RoomTypeRow key={rt.id} rt={rt} onEdit={openEditModal} t={t} />
            ))
          )}
        </M3Table>
      )}

      {isModalOpen && (
        <RoomTypeFormModal
          roomType={editingType}
          onClose={closeModal}
          onSaved={handleSaved}
        />
      )}
    </div>
  );
});

RoomTypeList.displayName = 'RoomTypeList';
