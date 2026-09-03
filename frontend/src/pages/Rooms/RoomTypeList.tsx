import { useState, useCallback, useMemo, memo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { RoomTypeResponse } from '../../types';
import { M3Button } from '../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { M3TableActionLink } from '../../components/m3';
import { M3LoadingState } from '../../components/m3';
import { M3ErrorState } from '../../components/m3';
import { M3TableEmptyRow } from '../../components/m3';
import { useRoomTypes } from '../../hooks/queries';
import { queryKeys } from '../../lib';
import { getErrorMessage } from '../../utils';
import { RoomTypeFormModal } from './RoomTypeFormModal';
import { RateSeasonManagerModal } from './RateSeasonManagerModal';

const RoomTypeRow = memo(({ rt, onEdit, onManageSeasons, t }: {
  rt: RoomTypeResponse;
  onEdit: (rt: RoomTypeResponse) => void;
  onManageSeasons: (rt: RoomTypeResponse) => void;
  t: (k: string) => string;
}) => {
  const handleEdit = useCallback(() => {
    onEdit(rt);
  }, [onEdit, rt]);

  const handleManageSeasons = useCallback(() => {
    onManageSeasons(rt);
  }, [onManageSeasons, rt]);

  return (
    <M3TableRow key={rt.id}>
      <M3TableCell className="font-medium">{rt.name}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">{rt.maxOccupancy}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">€ {rt.basePrice.toFixed(2)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant max-w-xs truncate" title={rt.description}>{rt.description || '-'}</M3TableCell>
      <M3TableCell className="text-right">
        <M3TableActionLink onClick={handleManageSeasons} className="lg:mr-4">
          {t('rate_seasons')}
        </M3TableActionLink>
        <M3TableActionLink onClick={handleEdit} className="lg:mr-4">
          {t('edit')}
        </M3TableActionLink>
      </M3TableCell>
    </M3TableRow>
  );
});

export const RoomTypeList = memo(() => {
  const { t } = useTranslation('common');
  const queryClient = useQueryClient();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<RoomTypeResponse | undefined>();
  const [seasonsRoomType, setSeasonsRoomType] = useState<RoomTypeResponse | undefined>();

  const { data: typesData, isLoading: loading, error: queryError, refetch } = useRoomTypes();
  const types = typesData ?? [];
  const error = queryError ? getErrorMessage(queryError, t('error_unexpected_fallback')) : null;

  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

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

  const openSeasonsModal = useCallback((roomType: RoomTypeResponse) => {
    setSeasonsRoomType(roomType);
  }, []);

  const closeSeasonsModal = useCallback(() => {
    setSeasonsRoomType(undefined);
  }, []);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    queryClient.invalidateQueries({ queryKey: queryKeys.roomTypes.all });
  }, [queryClient]);

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
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_room_types')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3Table headers={headers}>
          {types.length === 0 ? (
            <M3TableEmptyRow colSpan={headers.length} message={t('no_rooms_found')} />
          ) : (
            types.map((rt) => (
              <RoomTypeRow key={rt.id} rt={rt} onEdit={openEditModal} onManageSeasons={openSeasonsModal} t={t} />
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

      {seasonsRoomType && (
        <RateSeasonManagerModal
          roomType={seasonsRoomType}
          onClose={closeSeasonsModal}
        />
      )}
    </div>
  );
});

RoomTypeList.displayName = 'RoomTypeList';
