import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import type { ColumnDef, SortingState } from '@tanstack/react-table';
import type { GuestResponseDTO } from '../types';
import { PageHeader } from '../components/PageHeader';
import { ListToolbar } from '../components/ListToolbar';
import { M3Avatar, M3Button } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { M3TableActionLink } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import { useGuestsSearch, useDeleteGuest } from '../hooks/queries';
import { useDebounce, useListRangeSummary } from '../hooks';
import { queryKeys } from '../lib';
import { getErrorMessage } from '../utils';
import { guestService } from '../services';
import { GuestFormModal } from './GuestFormModal';

const PAGE_SIZE = 20;
const DEFAULT_SORT_FIELD = 'lastName';
const DEFAULT_SORT_DIR: 'asc' | 'desc' = 'asc';

interface ActionsCellProps {
  guest: GuestResponseDTO;
  onEdit: (g: GuestResponseDTO) => void;
  onDelete?: (g: GuestResponseDTO) => void;
  onExport?: (g: GuestResponseDTO) => void;
  t: TFunction;
}

const ActionsCell = ({ guest, onEdit, onDelete, onExport, t }: ActionsCellProps) => {
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleDeleteClick = useCallback(() => onDelete?.(guest), [onDelete, guest]);
  const handleExportClick = useCallback(() => onExport?.(guest), [onExport, guest]);

  return (
    <div className="text-right">
      <M3TableActionLink onClick={handleEdit}>
        {t('edit')}
      </M3TableActionLink>
      {onExport && (
        <M3TableActionLink
          className="ml-3"
          aria-label={`${t('export_guest_data')} ${guest.firstName} ${guest.lastName}`}
          onClick={handleExportClick}
        >
          {t('export_guest_data')}
        </M3TableActionLink>
      )}
      {onDelete && (
        <M3TableActionLink
          tone="error"
          className="ml-3"
          aria-label={`${t('delete')} ${guest.firstName} ${guest.lastName}`}
          onClick={handleDeleteClick}
        >
          {t('delete')}
        </M3TableActionLink>
      )}
    </div>
  );
};

export const Guests = memo(() => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | undefined>();
  const [guestToDelete, setGuestToDelete] = useState<GuestResponseDTO | null>(null);
  const [guestToExport, setGuestToExport] = useState<GuestResponseDTO | null>(null);
  const [exporting, setExporting] = useState(false);
  const [searchParams] = useSearchParams();
  const initialSearch = searchParams.get('search') ?? '';
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const debouncedSearch = useDebounce(searchQuery);

  const handleExportCsv = useCallback(async () => {
    try {
      await guestService.exportGuestsCsv(searchQuery);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('csv_export_failed')), 'error');
    }
  }, [searchQuery, addToast, t]);

  const [sortField, setSortField] = useState(DEFAULT_SORT_FIELD);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(DEFAULT_SORT_DIR);

  // A new search query invalidates the current page — always restart from page 0.
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch, sortField, sortDir]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const sorting = useMemo<SortingState>(
    () => [{ id: sortField, desc: sortDir === 'desc' }],
    [sortField, sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    setSortField(next[0].id);
    setSortDir(next[0].desc ? 'desc' : 'asc');
  }, []);

  const {
    data,
    isLoading: loading,
    error: queryError,
    refetch,
  } = useGuestsSearch(debouncedSearch, page, PAGE_SIZE, `${sortField},${sortDir}`);
  const guests = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;
  const summary = useListRangeSummary(page, PAGE_SIZE, guests.length, data?.totalElements ?? 0);
  const error = queryError ? getErrorMessage(queryError, t('error_unexpected_fallback')) : null;

  const deleteGuestMutation = useDeleteGuest();
  const deleting = deleteGuestMutation.isPending;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const handleOpenAddModal = useCallback(() => {
    setSelectedGuest(undefined);
    setIsModalOpen(true);
  }, []);

  const handleOpenEditModal = useCallback((guest: GuestResponseDTO) => {
    setSelectedGuest(guest);
    setIsModalOpen(true);
  }, []);

  const handleCloseModal = useCallback(() => {
    setIsModalOpen(false);
  }, []);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    queryClient.invalidateQueries({ queryKey: queryKeys.guests.all });
  }, [queryClient]);

  const handleDeleteRequest = useCallback((guest: GuestResponseDTO) => {
    setGuestToDelete(guest);
  }, []);

  const handleDeleteCancel = useCallback(() => {
    setGuestToDelete(null);
  }, []);

  const handleDeleteConfirm = useCallback(async () => {
    if (!guestToDelete) return;
    try {
      await deleteGuestMutation.mutateAsync(guestToDelete.id);
      addToast(t('guest_deleted_success'), 'success');
    } catch (err: unknown) {
      const e = err as { response?: { status?: number } };
      if (e.response?.status === 451) {
        addToast(t('delete_guest_gdpr_hold'), 'error');
      } else {
        addToast(t('delete_guest_failed'), 'error');
      }
    } finally {
      setGuestToDelete(null);
    }
  }, [guestToDelete, addToast, t, deleteGuestMutation]);

  const handleExportRequest = useCallback((guest: GuestResponseDTO) => {
    setGuestToExport(guest);
  }, []);

  const handleExportCancel = useCallback(() => {
    setGuestToExport(null);
  }, []);

  const handleExportConfirm = useCallback(async () => {
    if (!guestToExport) return;
    setExporting(true);
    try {
      await guestService.downloadGuestDataExport(guestToExport.id);
      setGuestToExport(null);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('export_guest_data_failed')), 'error');
    } finally {
      setExporting(false);
    }
  }, [guestToExport, addToast, t]);

  const getGuestRowId = useCallback((g: GuestResponseDTO) => g.id, []);

  const columns = useMemo<ColumnDef<GuestResponseDTO>[]>(() => [
    {
      id: 'lastName',
      accessorKey: 'lastName',
      header: t('name'),
      cell: ({ row }) => (
        <div className="flex items-center gap-3">
          <M3Avatar name={row.original.firstName || row.original.lastName} size="md" aria-hidden="true" />
          <div className="min-w-0">
            <p className="font-medium">{row.original.firstName} {row.original.lastName}</p>
            {row.original.email && <p className="text-xs text-on-surface-variant">{row.original.email}</p>}
          </div>
        </div>
      ),
    },
    {
      id: 'phone',
      header: t('phone'),
      enableSorting: false,
      cell: ({ row }) => <span className="text-on-surface-variant">{row.original.phone || '-'}</span>,
    },
    {
      id: 'city',
      header: t('city'),
      enableSorting: false,
      cell: ({ row }) => (
        <span className="text-on-surface-variant">{row.original.city || '-'} ({row.original.country || '-'})</span>
      ),
    },
    {
      id: 'actions',
      header: () => <span className="sr-only">{t('actions')}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <ActionsCell
          guest={row.original}
          onEdit={handleOpenEditModal}
          onDelete={isAdminOrOwner ? handleDeleteRequest : undefined}
          onExport={isAdminOrOwner ? handleExportRequest : undefined}
          t={t}
        />
      ),
    },
  ], [t, handleOpenEditModal, isAdminOrOwner, handleDeleteRequest, handleExportRequest]);

  return (
    <div className="space-y-6">
      <PageHeader
        icon="group"
        title={t('nav_guests')}
        subtitle={t('guests_subtitle')}
        actions={
          <M3Button icon="add" onClick={handleOpenAddModal}>
            {t('add_guest')}
          </M3Button>
        }
      />

      <ListToolbar
        searchLabel={t('search_placeholder')}
        searchValue={searchQuery}
        onSearchChange={handleSearchChange}
        trailing={isAdminOrOwner && (
          <M3Button icon="download" variant="tonal" onClick={handleExportCsv}>
            {t('export_csv')}
          </M3Button>
        )}
      />

      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_guests')}
          message={error}
          retryLabel={t('try_again')}
          onRetry={handleRetry}
        />
      ) : (
        <M3DataTable
          data={guests}
          columns={columns}
          getRowId={getGuestRowId}
          sorting={sorting}
          onSortingChange={handleSortingChange}
          emptyMessage={t('no_guests_found')}
        />
      )}

      {!loading && !error && (
        <M3Pagination
          page={page}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
          pageLabel={t('pagination')}
          prevLabel={t('prev_page')}
          nextLabel={t('next_page')}
          pageOfLabel={pageOfLabel}
          summary={summary}
        />
      )}

      {isModalOpen && (
        <GuestFormModal
          guest={selectedGuest}
          onClose={handleCloseModal}
          onSaved={handleSaved}
        />
      )}

      {guestToDelete && (
        <M3ConfirmDialog
          title={t('delete')}
          titleId="confirm-delete-guest-dialog"
          message={t('delete_guest_confirm')}
          confirmLabel={t('delete')}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteCancel}
          loading={deleting}
        />
      )}

      {guestToExport && (
        <M3ConfirmDialog
          title={t('export_guest_data_confirm_title')}
          titleId="confirm-export-guest-dialog"
          message={t('export_guest_data_confirm_body')}
          confirmLabel={t('export_guest_data_confirm_action')}
          onConfirm={handleExportConfirm}
          onCancel={handleExportCancel}
          loading={exporting}
        />
      )}
    </div>
  );
});

Guests.displayName = 'Guests';
