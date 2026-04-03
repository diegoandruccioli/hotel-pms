import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { stayService } from '../services/stayService';
import { useToastStore } from '../store/toastStore';
import type { StayResponse, StayStatus } from '../types/stay.types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3/M3Button';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3/M3Table';
import { M3StatusChip } from '../components/m3/M3StatusChip';
import { M3Card } from '../components/m3/M3Card';
import { useTranslation } from 'react-i18next';

const getTodayString = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const getStatusTone = (status: StayStatus) => {
  switch (status) {
    case 'CHECKED_IN': return 'success' as const;
    case 'CHECKED_OUT': return 'neutral' as const;
    case 'EXPECTED': return 'info' as const;
    default: return 'neutral' as const;
  }
};

const StayRow = memo(({ stay, onCheckOut, checkingOut, formatDate, getStatusTone, t }: {
  stay: StayResponse;
  onCheckOut: (s: StayResponse) => void;
  checkingOut: string | null;
  formatDate: (d?: string) => string;
  getStatusTone: (s: StayStatus) => "success" | "neutral" | "info";
  t: (k: string) => string;
}) => {
  const handleCheckOut = useCallback(() => {
    onCheckOut(stay);
  }, [onCheckOut, stay]);

  return (
    <M3TableRow key={stay.id}>
      <M3TableCell className="font-medium">
        <span className="truncate block max-w-[120px]" title={stay.roomId}>{stay.roomId.substring(0, 8)}…</span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">
        <span className="truncate block max-w-[120px]" title={stay.guestId}>{stay.guestId.substring(0, 8)}…</span>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckInTime)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(stay.actualCheckOutTime)}</M3TableCell>
      <M3TableCell>
        <div className="font-medium flex items-center gap-1.5 text-on-surface">
          <MaterialIcon name="group" size={18} />
          <span>{stay.guests?.length || 0}</span>
        </div>
      </M3TableCell>
      <M3TableCell>
        <M3StatusChip label={stay.status.replace('_', ' ')} tone={getStatusTone(stay.status)} />
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
            className="text-xs h-8 px-3"
          >
            {t('action_checkout')}
          </M3Button>
        )}
      </M3TableCell>
    </M3TableRow>
  );
});

export const Stays = memo(() => {
  const { t, i18n } = useTranslation('common');
  const navigate = useNavigate();
  const [stays, setStays] = useState<StayResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [checkingOut, setCheckingOut] = useState<string | null>(null);
  const [alloggiatiDate, setAlloggiatiDate] = useState(getTodayString());
  const [downloadingReport, setDownloadingReport] = useState(false);
  const addToast = useToastStore((s) => s.addToast);

  const loadStays = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await stayService.getAllStays();
      setStays(data);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : t('failed_load_stays');
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [t]);

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

  const handleNewCheckIn = useCallback(() => navigate('/reservations'), [navigate]);
  
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
    t('guests', 'Guests'), 
    t('status'), 
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
        <M3Button icon="add" onClick={handleNewCheckIn}>
          {t('new_checkin', 'New Check-in')}
        </M3Button>
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
          {stays.length === 0 ? (
            <tr><td colSpan={7} className="py-8 text-center text-sm font-body text-on-surface-variant">{t('no_active_stays')}</td></tr>
          ) : (
            stays.map((stay) => (
              <StayRow 
                key={stay.id} 
                stay={stay} 
                onCheckOut={handleCheckOut} 
                checkingOut={checkingOut}
                formatDate={formatDate}
                getStatusTone={getStatusTone}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}

      {/* Police Report / Alloggiati Web Section */}
      <M3Card variant="outlined" className="p-5">
        <div className="flex items-center gap-2 mb-3">
          <MaterialIcon name="verified_user" size={20} className="text-primary" />
          <h3 className="text-sm font-display font-semibold text-on-surface">{t('police_report_title')}</h3>
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
        </div>
      </M3Card>
    </div>
  );
});

Stays.displayName = 'Stays';
