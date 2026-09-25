import { useState, useCallback, useMemo, memo } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { ReservationGroupResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../utils';
import { useReservationGroups } from '../hooks/queries';

const PAGE_SIZE = 20;

interface GroupNameCellProps {
  group: ReservationGroupResponse;
  onView: (id: string) => void;
}

const GroupNameCell = memo(({ group, onView }: GroupNameCellProps) => {
  const handleClick = useCallback(() => onView(group.id), [onView, group.id]);
  return (
    <button
      type="button"
      className="text-primary hover:underline text-sm font-medium text-left"
      onClick={handleClick}
    >
      {group.name}
    </button>
  );
});

GroupNameCell.displayName = 'GroupNameCell';

const getStatusTone = (status: ReservationGroupResponse['status']) => {
  switch (status) {
    case 'CHECKED_OUT': return 'success' as const;
    case 'CHECKED_IN': return 'warning' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

export const ReservationGroups = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data: groupsPage, isLoading, error: queryError, refetch } = useReservationGroups(page, PAGE_SIZE);
  const groups = groupsPage?.content ?? [];
  const totalPages = groupsPage?.totalPages ?? 1;
  const error = queryError ? getErrorMessage(queryError, t('groups_load_failed')) : null;

  const handleNewGroup = useCallback(() => navigate('/reservations/groups/new'), [navigate]);
  const handleViewReservations = useCallback(() => navigate('/reservations'), [navigate]);
  const handleViewGroup = useCallback((id: string) => navigate(`/reservations/groups/${id}`), [navigate]);
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );
  const getGroupRowId = useCallback((group: ReservationGroupResponse) => group.id, []);
  const sorting = useMemo<SortingState>(() => [{ id: 'checkInDate', desc: true }], []);
  const handleSortingChange = useCallback(() => {
    // Server-side sort is fixed (newest check-in first) for now.
  }, []);
  const getStatusLabel = useCallback(
    (status: ReservationGroupResponse['status']) => t(`group_status_${status.toLowerCase()}`),
    [t],
  );

  const columns = useMemo<ColumnDef<ReservationGroupResponse>[]>(() => [
    {
      id: 'name',
      header: t('group_name'),
      cell: ({ row }) => <GroupNameCell group={row.original} onView={handleViewGroup} />,
    },
    {
      id: 'companyName',
      header: t('company_name'),
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.companyName ?? '—'}</span>,
    },
    {
      id: 'checkInDate',
      header: t('label_checkin_date'),
      cell: ({ row }) => <span>{row.original.checkInDate}</span>,
    },
    {
      id: 'checkOutDate',
      header: t('label_checkout_date'),
      cell: ({ row }) => <span>{row.original.checkOutDate}</span>,
    },
    {
      id: 'rooms',
      header: t('rooming_list'),
      cell: ({ row }) => <span>{row.original.members.length}</span>,
    },
    {
      id: 'status',
      header: t('status'),
      cell: ({ row }) => (
        <M3StatusChip label={getStatusLabel(row.original.status)} tone={getStatusTone(row.original.status)} />
      ),
    },
  ], [t, handleViewGroup, getStatusLabel]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="groups" className="mr-2 text-primary" />
            {t('nav_reservation_groups')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('reservation_groups_subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <M3Button
            data-testid="view-reservations-btn"
            icon="event"
            variant="outlined"
            onClick={handleViewReservations}
          >
            {t('nav_reservations')}
          </M3Button>
          <M3Button icon="add" onClick={handleNewGroup}>
            {t('new_group')}
          </M3Button>
        </div>
      </div>

      {isLoading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('groups_load_failed')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={refetch}
        />
      ) : (
        <M3DataTable
          data={groups}
          columns={columns}
          getRowId={getGroupRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={t('no_groups_found')}
        />
      )}

      {!isLoading && !error && (
        <M3Pagination
          page={page}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
          pageLabel={t('pagination')}
          prevLabel={t('prev_page')}
          nextLabel={t('next_page')}
          pageOfLabel={pageOfLabel}
        />
      )}
    </div>
  );
};
