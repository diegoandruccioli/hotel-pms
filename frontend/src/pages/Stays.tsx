import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { stayService } from '../services/stayService';
import { useAuthStore } from '../store/authStore';
import { useToastStore } from '../store/toastStore';
import type { StayResponse, StayStatus } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';

const getTodayString = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

type StaySortField = 'actualCheckInTime' | 'expectedCheckOutDate' | 'status';
type SortDir = 'asc' | 'desc';

interface StaysNavState {
  statusFilter?: StayStatus | 'ALL';
  sortField?: StaySortField;
  sortDir?: SortDir;
}

const getStatusTone = (status: StayStatus) => {
  switch (status) {
    case 'CHECKED_IN': return 'success' as const;
    case 'CHECKED_OUT': return 'neutral' as const;
    case 'EXPECTED': return 'info' as const;
    default: return 'neutral' as const;
  }
};

const StayRow = memo(({ stay, onCheckOut, checkingOut, formatDate, getStatusTone, t, onGuestClick }: {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  formatDate: (d?: string) => string;
  getStatusTone: (s: StayStatus) => "success" | "neutral" | "info";
  t: TFunction;
  onGuestClick: (guestDisplayName: string) => void;
}) => {
  const handleCheckOut = useCallback(() => {
    onCheckOut(stay);
  }, [onCheckOut, stay]);

  const handleGuestNameClick = useCallback(() => {
    onGuestClick(stay.guestDisplayName ?? stay.guestId);
  }, [onGuestClick, stay.guestDisplayName, stay.guestId]);

  return (
    <M3TableRow key={stay.id}>
      <M3TableCell className="font-medium">
        <span className="truncate block max-w-[120px]" title={stay.roomId}>
          {stay.roomNumber ?? `${stay.roomId.substring(0, 8)}…`}
        </span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">
        <button
          type="button"
          onClick={handleGuestNameClick}
          className="truncate block max-w-[120px] text-left text-primary hover:text-primary/80 font-medium text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-1 rounded"
          title={stay.guestId}
        >
          {stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`}
        </button>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckInTime)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckOutTime)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{stay.expectedCheckOutDate ?? '-'}</M3TableCell>
      <M3TableCell>
        <div className="font-medium flex items-center gap-1.5 text-on-surface">
          <MaterialIcon name="group" size={18} />
          <span>{stay.guests?.length || 0}</span>
        </div>
      </M3TableCell>
      <M3TableCell>
        <M3StatusChip
          label={t(`status_${stay.status.toLowerCase()}`, stay.status.replace('_', ' '))}
          tone={getStatusTone(stay.status)}
        />
      </M3TableCell>
      <M3TableCell>
        <span title={stay.alloggiatiSendFailed ? stay.alloggiatiFailureReason ?? undefined : undefined}>
          <M3StatusChip
            label={
              stay.alloggiatiSent
                ? t('alloggiati_sent')
                : stay.alloggiatiSendFailed
                  ? t('alloggiati_failed')
                  : t('alloggiati_not_sent')
            }
            tone={stay.alloggiatiSent ? 'success' : stay.alloggiatiSendFailed ? 'error' : 'neutral'}
          />
        </span>
      </M3TableCell>
      <M3TableCell className="text-right">
        {stay.status === 'CHECKED_IN' && (
          <M3Button
            variant="tonal"
            icon={checkingOut === stay.id ? 'progress_activity' : 'logout'}
            loading={checkingOut === stay.id}
            disabled={checkingOut === stay.id}
            onClick={handleCheckOut}
            id={`checkout-btn-${stay.id}`}
            className="text-xs h-10 px-3"
          >
            {t('action_checkout')}
          </M3Button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});

const StayStatusChip = memo(({ value, active, label, onClick }: {
  value: StayStatus | 'ALL';
  active: boolean;
  label: string;
  onClick: (v: StayStatus | 'ALL') => void;
}) => {
  const handleClick = useCallback(() => onClick(value), [onClick, value]);
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={handleClick}
      className={`px-3 py-1.5 rounded-full text-xs font-medium font-body border transition-colors ${
        active
          ? 'bg-primary text-on-primary border-primary'
          : 'bg-transparent text-on-surface-variant border-outline-variant hover:border-outline'
      }`}
    >
      {label}
    </button>
  );
});
StayStatusChip.displayName = 'StayStatusChip';

export const Stays = memo(() => {
  const { t, i18n } = useTranslation('common');
  const navigate = useNavigate();
  const location = useLocation();
  const navState = location.state as StaysNavState | null;
  const [stays, setStays] = useState<StayResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checkingOut, setCheckingOut] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StayStatus | 'ALL'>(() => navState?.statusFilter ?? 'ALL');
  const [sortField, setSortField] = useState<StaySortField>(() => navState?.sortField ?? 'actualCheckInTime');
  const [sortDir, setSortDir] = useState<SortDir>(() => navState?.sortDir ?? 'desc');
  const [alloggiatiDate, setAlloggiatiDate] = useState(getTodayString());
  const [downloadingReport, setDownloadingReport] = useState(false);
  const [downloadingJson, setDownloadingJson] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const addToast = useToastStore((s) => s.addToast);
  const role = useAuthStore((s) => s.user?.role);
  const isAdminOrOwner = role === 'ADMIN' || role === 'OWNER';

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(id);
  }, [searchQuery]);

  const handleSearchChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(e.target.value);
  }, []);

  const handleStatusFilterClick = useCallback((s: StayStatus | 'ALL') => {
    setStatusFilter(s);
  }, []);

  const handleSortFieldChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSortField(e.target.value as StaySortField);
  }, []);

  const toggleSortDir = useCallback(() => {
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  }, []);

  const filteredStays = useMemo(() => {
    let result = stays;
    if (statusFilter !== 'ALL') {
      result = result.filter((s) => s.status === statusFilter);
    }
    if (debouncedSearch.trim()) {
      const q = debouncedSearch.toLowerCase();
      result = result.filter(
        (s) =>
          s.roomNumber?.toLowerCase().includes(q) ||
          s.guestDisplayName?.toLowerCase().includes(q),
      );
    }
    const sorted = [...result].sort((a, b) => {
      const cmp = (a[sortField] ?? '').localeCompare(b[sortField] ?? '');
      return sortDir === 'asc' ? cmp : -cmp;
    });
    return sorted;
  }, [stays, statusFilter, debouncedSearch, sortField, sortDir]);

  const loadStays = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await stayService.getAllStays(page);
      setStays(data.content);
      setTotalPages(data.totalPages);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_load_stays');
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [page, t]);

  useEffect(() => {
    loadStays();
  }, [loadStays]);

  const handleCheckOut = useCallback(async (stay: StayResponse) => {
    setCheckingOut(stay.id);
    try {
      const updated = await stayService.checkOut(stay.id);
      setStays((prev) => prev.map((s) => (s.id === stay.id ? updated : s)));
      addToast(t('guest_checked_out_success'), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('checkout_failed');
      addToast(message, 'error');
    } finally {
      setCheckingOut(null);
    }
  }, [addToast, t]);

  const handleAlloggiatiDownload = useCallback(async () => {
    setDownloadingReport(true);
    try {
      await stayService.downloadAlloggiatiReport(alloggiatiDate);
      addToast(t('alloggiati_report_downloaded', { date: alloggiatiDate }), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_generate_report');
      addToast(message, 'error');
    } finally {
      setDownloadingReport(false);
    }
  }, [alloggiatiDate, addToast, t]);

  const handleAlloggiatiJsonDownload = useCallback(async () => {
    setDownloadingJson(true);
    try {
      await stayService.downloadAlloggiatiJson(alloggiatiDate);
      addToast(t('alloggiati_json_downloaded', { date: alloggiatiDate }), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_generate_report');
      addToast(message, 'error');
    } finally {
      setDownloadingJson(false);
    }
  }, [alloggiatiDate, addToast, t]);

  const handleNewCheckIn = useCallback(() => navigate('/reservations'), [navigate]);
  const handleWalkIn = useCallback(() => navigate('/stays/walk-in'), [navigate]);
  const handleGuestNavigate = useCallback((guestDisplayName: string) => {
    navigate('/guests?search=' + encodeURIComponent(guestDisplayName));
  }, [navigate]);
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);
  
  const handleAlloggiatiSubmit = useCallback(async () => {
    const confirmed = window.confirm(t('alloggiati_submit_confirm', { date: alloggiatiDate }));
    if (!confirmed) return;
    setSubmitting(true);
    try {
      await stayService.submitAlloggiatiReport(alloggiatiDate);
      addToast(t('alloggiati_submit_success'), 'success');
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '';
      addToast(message ? t('alloggiati_submit_error', { message }) : t('alloggiati_submit_failed'), 'error');
    } finally {
      setSubmitting(false);
    }
  }, [alloggiatiDate, addToast, t]);

  const handleAlloggiatiDateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setAlloggiatiDate(e.target.value);
  }, []);

  const formatDate = useCallback((dateStr?: string) => {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleString(i18n.language);
  }, [i18n.language]);

  const headers = useMemo(() => [
    t('room_id'),
    t('guest_id'),
    t('check_in'),
    t('check_out'),
    t('expected_checkout_col'),
    t('guests'),
    t('status'),
    t('alloggiati_column'),
    <span key="sr" className="sr-only">{t('actions')}</span>
  ], [t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center">
            <MaterialIcon name="hotel" className="mr-2 text-primary" />
            {t('nav_stays')}
          </h1>
          <p className="text-sm font-body text-on-surface-variant mt-1">{t('stays_subtitle')}</p>
        </div>
        <div className="flex gap-2">
          <M3Button icon="add" onClick={handleNewCheckIn}>
            {t('new_checkin', 'New Check-in')}
          </M3Button>
          <M3Button icon="person_add" variant="outlined" onClick={handleWalkIn}>
            {t('walkin_title', 'Walk-in')}
          </M3Button>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center gap-3">
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
        <div className="flex flex-wrap gap-2" role="group" aria-label={t('filter_status')}>
          {(['ALL', 'EXPECTED', 'CHECKED_IN', 'CHECKED_OUT'] as const).map((s) => (
            <StayStatusChip
              key={s}
              value={s}
              active={statusFilter === s}
              label={s === 'ALL' ? t('filter_all') : s === 'EXPECTED' ? t('status_expected') : s === 'CHECKED_IN' ? t('status_checked_in') : t('status_checked_out')}
              onClick={handleStatusFilterClick}
            />
          ))}
        </div>
        <div className="flex items-center gap-2">
          <label htmlFor="stays-sort-field" className="sr-only">{t('sort_by')}</label>
          <select
            id="stays-sort-field"
            value={sortField}
            onChange={handleSortFieldChange}
            className="pl-3 pr-8 py-2 rounded-shape-xs border border-outline bg-transparent text-sm font-body text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
          >
            <option value="actualCheckInTime">{t('check_in')}</option>
            <option value="expectedCheckOutDate">{t('expected_checkout_col')}</option>
            <option value="status">{t('status')}</option>
          </select>
          <button
            type="button"
            onClick={toggleSortDir}
            aria-label={sortDir === 'asc' ? t('sort_dir_asc') : t('sort_dir_desc')}
            className="flex items-center justify-center w-10 h-10 rounded-shape-full border border-outline text-on-surface-variant hover:bg-primary/[0.08] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 transition-colors"
          >
            <MaterialIcon name={sortDir === 'asc' ? 'arrow_upward' : 'arrow_downward'} size={20} />
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center items-center h-64 bg-surface rounded-shape-md shadow-elevation-1">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : error ? (
        <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <div>
            <h3 className="text-sm font-medium font-body">{t('error_loading_stays')}</h3>
            <p className="mt-1 text-sm font-body opacity-80">{error}</p>
            <button type="button" onClick={loadStays} className="mt-2 text-sm font-medium underline hover:no-underline">
              {t('try_again')}
            </button>
          </div>
        </div>
      ) : (
        <M3Table headers={headers}>
          {filteredStays.length === 0 ? (
            <tr><td colSpan={9} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_active_stays')}</td></tr>
          ) : (
            filteredStays.map((stay) => (
              <StayRow
                key={stay.id}
                stay={stay}
                onCheckOut={handleCheckOut}
                checkingOut={checkingOut}
                formatDate={formatDate}
                getStatusTone={getStatusTone}
                t={t}
                onGuestClick={handleGuestNavigate}
              />
            ))
          )}
        </M3Table>
      )}

      {/* Pagination */}
      {!loading && !error && totalPages > 1 && (
        <nav aria-label={t('pagination')} className="flex items-center justify-center gap-3">
          <M3Button
            variant="outlined"
            icon="chevron_left"
            disabled={page === 0}
            onClick={handlePrevPage}
            aria-label={t('prev_page')}
          >
            {t('prev_page')}
          </M3Button>
          <span className="text-sm font-body text-on-surface-variant">
            {t('page_x_of_y', { current: page + 1, total: totalPages })}
          </span>
          <M3Button
            variant="outlined"
            icon="chevron_right"
            disabled={page >= totalPages - 1}
            onClick={handleNextPage}
            aria-label={t('next_page')}
          >
            {t('next_page')}
          </M3Button>
        </nav>
      )}

      {/* Police Report / Alloggiati Web Section */}
      <M3Card variant="outlined" className="p-5">
        <div className="flex items-center gap-2 mb-3">
          <MaterialIcon name="verified_user" size={20} className="text-primary" />
          <h2 className="text-sm font-display font-semibold text-on-surface">{t('police_report_title')}</h2>
        </div>
        <p className="text-xs font-body text-on-surface-variant mb-4">{t('police_report_desc')}</p>
        <div className="flex flex-col sm:flex-row items-end gap-3">
          <div className="flex-1">
            <label htmlFor="alloggiati-date" className="block text-sm font-medium font-body text-on-surface-variant mb-1">
              {t('check_in_date')}
            </label>
            <input
              id="alloggiati-date"
              type="date"
              value={alloggiatiDate}
              onChange={handleAlloggiatiDateChange}
              className="block w-full rounded-shape-xs border border-outline px-3 py-2 text-sm font-body bg-transparent text-on-surface focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none"
            />
          </div>
          <M3Button
            id="generate-alloggiati-btn"
            icon={downloadingReport ? 'progress_activity' : 'download'}
            loading={downloadingReport}
            disabled={downloadingReport}
            onClick={handleAlloggiatiDownload}
          >
            {t('generate_and_download')}
          </M3Button>
          {isAdminOrOwner && (
            <M3Button
              id="download-alloggiati-json-btn"
              variant="outlined"
              icon={downloadingJson ? 'progress_activity' : 'data_object'}
              loading={downloadingJson}
              disabled={downloadingJson}
              onClick={handleAlloggiatiJsonDownload}
            >
              {t('download_json_export')}
            </M3Button>
          )}
          {isAdminOrOwner && (
            <M3Button
              id="submit-alloggiati-btn"
              variant="tonal"
              icon={submitting ? 'progress_activity' : 'send'}
              loading={submitting}
              disabled={submitting}
              onClick={handleAlloggiatiSubmit}
            >
              {t('alloggiati_submit')}
            </M3Button>
          )}
        </div>
      </M3Card>
    </div>
  );
});

Stays.displayName = 'Stays';
