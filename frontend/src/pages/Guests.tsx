import { useState, useEffect, useCallback, useMemo, useRef, memo } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import type { SortingState } from '@tanstack/react-table';
import type { GuestResponseDTO } from '../types';
import { PageHeader } from '../components/PageHeader';
import { ListToolbar } from '../components/ListToolbar';
import { M3Button, M3EmptyState } from '../components/m3';
import { M3DataTable } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store';
import { useToastStore } from '../store';
import { useGuestsSearch, useDeleteGuest } from '../hooks/queries';
import { useDebounce, useListRangeSummary } from '../hooks';
import { queryKeys } from '../lib';
import { getErrorMessage } from '../utils';
import { guestService } from '../services';
import { GuestFormModal } from '../components/GuestFormModal';
import { GuestDetailSheet } from './Guests/GuestDetailSheet';
import { useGuestColumns } from './Guests/useGuestColumns';
import { guestListParamsToSearch, readGuestListParams, type GuestListParams } from './Guests/guestListParams';

const PAGE_SIZE = 20;
const SORT_FIELD = 'lastName';

export const Guests = memo(() => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';
  const queryClient = useQueryClient();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | undefined>();
  // Where focus goes back to when the form opened from the detail sheet closes: the sheet is
  // unmounted by then, so the trap has no live opener to return to.
  const detailTriggerRef = useRef<HTMLElement | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const [detailGuest, setDetailGuest] = useState<GuestResponseDTO | null>(null);
  const [guestToDelete, setGuestToDelete] = useState<GuestResponseDTO | null>(null);
  const [guestToExport, setGuestToExport] = useState<GuestResponseDTO | null>(null);
  const [exporting, setExporting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  // Sort direction and page live in the URL (back, refresh and shared links keep them). The search
  // text does not, because it holds guest names: an old `?search=` link is read once, then dropped.
  const [urlParams, setUrlParams] = useSearchParams();
  const { page, dir: sortDir } = readGuestListParams(urlParams);
  const [searchQuery, setSearchQuery] = useState(() => urlParams.get('search') ?? '');
  const debouncedSearch = useDebounce(searchQuery);

  const handleExportCsv = useCallback(async () => {
    try {
      await guestService.exportGuestsCsv(searchQuery);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('csv_export_failed')), 'error');
    }
  }, [searchQuery, addToast, t]);

  const updateList = useCallback((patch: Partial<GuestListParams>) => {
    setUrlParams(guestListParamsToSearch({ page, dir: sortDir, ...patch }), { replace: true });
  }, [page, sortDir, setUrlParams]);

  const hasLegacySearchParam = urlParams.has('search');
  useEffect(() => {
    if (hasLegacySearchParam) updateList({});
  }, [hasLegacySearchParam, updateList]);

  // A new search query invalidates the current page: restart from the first one.
  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
    if (page > 0) updateList({ page: 0 });
  }, [page, updateList]);

  const handlePrevPage = useCallback(() => updateList({ page: page - 1 }), [updateList, page]);
  const handleNextPage = useCallback(() => updateList({ page: page + 1 }), [updateList, page]);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const sorting = useMemo<SortingState>(
    () => [{ id: SORT_FIELD, desc: sortDir === 'desc' }],
    [sortDir],
  );

  const handleSortingChange = useCallback((next: SortingState) => {
    updateList({ dir: next[0].desc ? 'desc' : 'asc', page: 0 });
  }, [updateList]);

  const {
    data,
    isLoading: loading,
    error: queryError,
    refetch,
  } = useGuestsSearch(debouncedSearch, page, PAGE_SIZE, `${SORT_FIELD},${sortDir}`);
  const guests = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;
  const summary = useListRangeSummary(page, PAGE_SIZE, guests.length, data?.totalElements ?? 0);
  const error = queryError ? getErrorMessage(queryError, t('error_unexpected_fallback')) : null;

  // A page past the end (hand-edited URL, or the list shrank) lands on the last one in silence.
  const lastPage = data ? Math.max(data.totalPages - 1, 0) : 0;
  useEffect(() => {
    if (data && page > lastPage) updateList({ page: lastPage });
  }, [data, page, lastPage, updateList]);

  const showSearchEmpty = debouncedSearch !== '' && guests.length === 0;
  const handleShowAll = useCallback(() => {
    setSearchQuery('');
    if (page > 0) updateList({ page: 0 });
  }, [page, updateList]);

  const deleteGuestMutation = useDeleteGuest();
  const deleting = deleteGuestMutation.isPending;
  const handleRetry = useCallback(() => { refetch(); }, [refetch]);

  const handleOpenAddModal = useCallback(() => {
    setSelectedGuest(undefined);
    setIsModalOpen(true);
  }, []);

  // The detail sheet and the form are never open together: editing from the sheet closes it first.
  const handleOpenEditModal = useCallback((guest: GuestResponseDTO) => {
    setDetailGuest(null);
    setSelectedGuest(guest);
    setIsModalOpen(true);
  }, []);

  const handleOpenDetail = useCallback((guest: GuestResponseDTO, trigger: HTMLElement) => {
    detailTriggerRef.current = trigger;
    setDetailGuest(guest);
  }, []);
  const handleEditFromDetail = useCallback((guest: GuestResponseDTO) => {
    returnFocusRef.current = detailTriggerRef.current;
    handleOpenEditModal(guest);
  }, [handleOpenEditModal]);
  const handleNewReservationFromDetail = useCallback((guest: GuestResponseDTO) => {
    navigate('/reservations/new', { state: { guest } });
  }, [navigate]);
  // Deep link (e.g. a quotation's guest): open that guest's detail sheet on arrival.
  const linkedGuestId = (location.state as { guestId?: string } | null)?.guestId;
  useEffect(() => {
    if (!linkedGuestId) return undefined;
    let cancelled = false;
    guestService.getGuestById(linkedGuestId)
      .then((guest) => { if (!cancelled) setDetailGuest(guest); })
      .catch((err: unknown) => {
        if (!cancelled) addToast(getErrorMessage(err, t('error_unexpected_fallback')), 'error');
      });
    return () => { cancelled = true; };
  }, [linkedGuestId, addToast, t]);

  const restoreFocus = useCallback(() => {
    const target = returnFocusRef.current;
    returnFocusRef.current = null;
    if (target?.isConnected) window.setTimeout(() => target.focus(), 0);
  }, []);
  const handleCloseDetail = useCallback(() => setDetailGuest(null), []);

  const handleCloseModal = useCallback(() => {
    setIsModalOpen(false);
    restoreFocus();
  }, [restoreFocus]);

  const handleSaved = useCallback(() => {
    setIsModalOpen(false);
    queryClient.invalidateQueries({ queryKey: queryKeys.guests.all });
    restoreFocus();
  }, [queryClient, restoreFocus]);

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

  const columns = useGuestColumns({
    isAdminOrOwner,
    onOpen: handleOpenDetail,
    onEdit: handleOpenEditModal,
    onDelete: handleDeleteRequest,
    onExport: handleExportRequest,
  });

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
        searchPlaceholder={t('guests_search_hint')}
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
      ) : showSearchEmpty ? (
        <M3EmptyState
          icon="person_search"
          title={t('guests_no_match_title')}
          description={t('guests_no_match_hint')}
          action={
            <M3Button variant="outlined" onClick={handleShowAll}>
              {t('guests_show_all')}
            </M3Button>
          }
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

      {detailGuest && (
        <GuestDetailSheet guest={detailGuest} onClose={handleCloseDetail} onEdit={handleEditFromDetail} onNewReservation={handleNewReservationFromDetail} />
      )}

      {guestToDelete && (
        <M3ConfirmDialog
          title={t('delete_guest_title', { name: `${guestToDelete.firstName} ${guestToDelete.lastName}` })}
          titleId="confirm-delete-guest-dialog"
          message={t('delete_guest_confirm')}
          confirmLabel={t('delete_guest_action')}
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
