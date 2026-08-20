import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import type { GuestResponseDTO } from '../types/guest.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3Dialog } from '../components/m3/M3Dialog';
import { M3TableActionLink } from '../components/m3/M3TableActionLink';
import { M3LoadingState } from '../components/m3/M3LoadingState';
import { M3ErrorState } from '../components/m3/M3ErrorState';
import { M3TableEmptyRow } from '../components/m3/M3EmptyState';
import { M3Pagination } from '../components/m3/M3Pagination';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import { useGuestsSearch, useDeleteGuest } from '../hooks/queries/useGuests';
import { queryKeys } from '../lib/queryKeys';
import { getErrorMessage } from '../utils/errorMessage';
import { GuestFormModal } from './GuestFormModal';

const PAGE_SIZE = 20;

interface GuestRowProps {
  guest: GuestResponseDTO;
  onEdit: (g: GuestResponseDTO) => void;
  onDelete?: (g: GuestResponseDTO) => void;
  t: (k: string) => string;
}

const GuestRow = memo(({ guest, onEdit, onDelete, t }: GuestRowProps) => {
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleDeleteClick = useCallback(() => onDelete?.(guest), [onDelete, guest]);

  return (
    <M3TableRow key={guest.id}>
      <M3TableCell className="font-medium">{guest.firstName} {guest.lastName}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.email}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.phone || '-'}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{guest.city || '-'} ({guest.country || '-'})</M3TableCell>
      <M3TableCell className="text-right">
        <M3TableActionLink onClick={handleEdit}>
          {t('edit')}
        </M3TableActionLink>
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
      </M3TableCell>
    </M3TableRow>
  );
});

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
  const [searchParams] = useSearchParams();
  const initialSearch = searchParams.get('search') ?? '';
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const [debouncedSearch, setDebouncedSearch] = useState(initialSearch);

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

  // A new search query invalidates the current page — always restart from page 0.
  useEffect(() => {
    setPage(0);
  }, [debouncedSearch]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('page_x_of_y', { current, total }),
    [t],
  );

  const {
    data,
    isLoading: loading,
    error: queryError,
    refetch,
  } = useGuestsSearch(debouncedSearch, page, PAGE_SIZE);
  const guests = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;
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

  const headers = useMemo(() => [
    t('name'),
    t('email'),
    t('phone'),
    t('city'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="group" className="mr-2 text-primary" />
            {t('nav_guests')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('guests_subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="relative">
            <MaterialIcon name="search" size={18} className="absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant pointer-events-none" />
            <input
              type="search"
              value={searchQuery}
              onChange={handleSearchChange}
              placeholder={t('search_placeholder')}
              aria-label={t('search_placeholder')}
              className="pl-9 pr-3 py-2 w-full sm:w-56 rounded-shape-xs border border-outline bg-transparent text-sm font-body text-on-surface placeholder:text-on-surface-variant focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            />
          </div>
          <M3Button icon="add" onClick={handleOpenAddModal}>
            {t('add_guest')}
          </M3Button>
        </div>
      </div>

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
        <M3Table headers={headers}>
          {guests.length === 0 ? (
            <M3TableEmptyRow colSpan={headers.length} message={t('no_guests_found')} />
          ) : (
            guests.map((guest) => (
              <GuestRow
                key={guest.id}
                guest={guest}
                onEdit={handleOpenEditModal}
                onDelete={isAdminOrOwner ? handleDeleteRequest : undefined}
                t={t}
              />
            ))
          )}
        </M3Table>
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
        <M3Dialog
          open
          title={t('delete')}
          titleId="confirm-delete-guest-dialog"
          onClose={handleDeleteCancel}
        >
          <p className="text-sm font-body text-on-surface">{t('delete_guest_confirm')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleDeleteCancel} disabled={deleting}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleDeleteConfirm} loading={deleting}>
              {t('delete')}
            </M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
});

Guests.displayName = 'Guests';
