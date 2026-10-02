import { useFormatters } from '../hooks';
import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { quotationService } from '../services';
import type { QuotationResponse } from '../types';
import { M3LoadingState } from '../components/m3';
import { M3TableEmptyRow } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3';
import { M3TableActionLink } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { useToastStore } from '../store';
import { getErrorMessage, quotationStatusTone } from '../utils';

const PAGE_SIZE = 20;

const formatTotal = (
  quotation: QuotationResponse,
  t: (key: string, opts?: Record<string, unknown>) => string,
  formatCurrency: (amount: number) => string,
): string => {
  if (quotation.options.length <= 1) {
    return formatCurrency(quotation.totalPrice);
  }
  const totals = quotation.options.map((o) => o.totalPrice);
  const min = Math.min(...totals);
  const max = Math.max(...totals);
  if (min === max) {
    return formatCurrency(min);
  }
  return t('price_range', { min: formatCurrency(min), max: formatCurrency(max) });
};

interface QuotationRowProps {
  quotation: QuotationResponse;
  onSend: (id: string) => void;
  onConvert: (id: string) => void;
  onDecline: (id: string) => void;
  onDelete: (id: string) => void;
  onDownload: (id: string) => void;
  sendingId: string | null;
  t: (key: string, opts?: Record<string, unknown>) => string;
}

const QuotationRow = memo(({ quotation, onSend, onConvert, onDecline, onDelete, onDownload, sendingId, t }: QuotationRowProps) => {
  const { formatCurrency } = useFormatters();
  const handleSend = useCallback(() => onSend(quotation.id), [onSend, quotation.id]);
  const handleConvert = useCallback(() => onConvert(quotation.id), [onConvert, quotation.id]);
  const handleDecline = useCallback(() => onDecline(quotation.id), [onDecline, quotation.id]);
  const handleDelete = useCallback(() => onDelete(quotation.id), [onDelete, quotation.id]);
  const handleDownload = useCallback(() => onDownload(quotation.id), [onDownload, quotation.id]);

  const canSend = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canConvert = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canDecline = quotation.status === 'DRAFT' || quotation.status === 'SENT';

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">
        <Link to={`/quotations/${quotation.id}`} className="hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs">
          {quotation.guestFullName}
        </Link>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{quotation.checkInDate}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{quotation.checkOutDate}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">{formatTotal(quotation, t, formatCurrency)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{quotation.validUntil}</M3TableCell>
      <M3TableCell>
        <div className="flex flex-col items-start gap-1">
          <M3StatusChip label={t(`status_${quotation.status.toLowerCase()}`)} tone={quotationStatusTone[quotation.status]} />
          {quotation.sendFailed && (
            <M3StatusChip label={t('send_failed_badge')} tone="error" />
          )}
        </div>
      </M3TableCell>
      <M3TableCell className="text-right">
        {canSend && (
          <M3TableActionLink className="mr-2" onClick={handleSend} disabled={sendingId === quotation.id}>
            {quotation.status === 'SENT' ? t('action_resend') : t('action_send')}
          </M3TableActionLink>
        )}
        {canConvert && (
          <M3TableActionLink className="mr-2" onClick={handleConvert}>
            {t('action_convert')}
          </M3TableActionLink>
        )}
        <M3TableActionLink className="mr-2" onClick={handleDownload}>
          {t('action_download_pdf')}
        </M3TableActionLink>
        {canDecline && (
          <M3TableActionLink className="mr-2" onClick={handleDecline}>
            {t('action_decline')}
          </M3TableActionLink>
        )}
        <M3TableActionLink tone="error" onClick={handleDelete}>
          {t('action_delete')}
        </M3TableActionLink>
      </M3TableCell>
    </M3TableRow>
  );
});
QuotationRow.displayName = 'QuotationRow';

export const Quotations = () => {
  const { t } = useTranslation(['quotations', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);

  const [quotations, setQuotations] = useState<QuotationResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [declineTarget, setDeclineTarget] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadQuotations = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await quotationService.getAllQuotations(page, PAGE_SIZE);
      setQuotations(data.content);
      setTotalPages(data.totalPages);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('error_loading_quotations')));
    } finally {
      setLoading(false);
    }
  }, [page, t]);

  useEffect(() => {
    loadQuotations();
  }, [loadQuotations]);

  const handleNew = useCallback(() => navigate('/quotations/new'), [navigate]);
  const handlePrevPage = useCallback(() => setPage((p) => p - 1), []);
  const handleNextPage = useCallback(() => setPage((p) => p + 1), []);

  const handleSend = useCallback(async (id: string) => {
    setSendingId(id);
    try {
      const updated = await quotationService.sendQuotation(id);
      setQuotations((prev) => prev.map((q) => (q.id === id ? updated : q)));
      addToast(updated.sendFailed ? t('toast_send_failed') : t('toast_sent'), updated.sendFailed ? 'error' : 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_send_failed')), 'error');
    } finally {
      setSendingId(null);
    }
  }, [addToast, t]);

  const handleConvert = useCallback(async (id: string) => {
    const target = quotations.find((q) => q.id === id);
    if (target && target.options.length > 1) {
      navigate(`/quotations/${id}`);
      return;
    }
    try {
      await quotationService.convertToReservation(id);
      addToast(t('toast_converted'), 'success');
      await loadQuotations();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_converted')), 'error');
    }
  }, [addToast, t, loadQuotations, quotations, navigate]);

  const handleDownload = useCallback(async (id: string) => {
    try {
      await quotationService.downloadPdf(id);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('download_failed', { ns: 'common' })), 'error');
    }
  }, [addToast, t]);

  const handleDeclineRequest = useCallback((id: string) => setDeclineTarget(id), []);
  const handleDeclineClose = useCallback(() => setDeclineTarget(null), []);
  const handleDeclineConfirm = useCallback(async () => {
    if (!declineTarget) return;
    setBusy(true);
    try {
      await quotationService.declineQuotation(declineTarget);
      addToast(t('toast_declined'), 'success');
      await loadQuotations();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_declined')), 'error');
    } finally {
      setBusy(false);
      setDeclineTarget(null);
    }
  }, [declineTarget, addToast, t, loadQuotations]);

  const handleDeleteRequest = useCallback((id: string) => setDeleteTarget(id), []);
  const handleDeleteClose = useCallback(() => setDeleteTarget(null), []);
  const handleDeleteConfirm = useCallback(async () => {
    if (!deleteTarget) return;
    setBusy(true);
    try {
      await quotationService.deleteQuotation(deleteTarget);
      addToast(t('toast_deleted'), 'success');
      await loadQuotations();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_deleted')), 'error');
    } finally {
      setBusy(false);
      setDeleteTarget(null);
    }
  }, [deleteTarget, addToast, t, loadQuotations]);

  const tableHeaders = useMemo(() => [
    t('col_guest'),
    t('col_check_in'),
    t('col_check_out'),
    t('col_total'),
    t('col_valid_until'),
    t('col_status'),
    <span key="sr" className="sr-only">{t('col_actions')}</span>,
  ], [t]);

  return (
    <div className="space-y-6">
      <PageHeader
        icon="request_quote"
        title={t('title')}
        subtitle={t('subtitle')}
        actions={
          <M3Button icon="add" onClick={handleNew}>{t('new_quotation')}</M3Button>
        }
      />

      {loading ? (
        <M3LoadingState label={t('common:loading')} />
      ) : error ? (
        <M3ErrorState
          title={t('error_loading_quotations')}
          message={error}
          retryLabel={t('common:try_again')}
          onRetry={loadQuotations}
        />
      ) : (
        <M3Table headers={tableHeaders}>
          {quotations.length === 0 ? (
            <M3TableEmptyRow colSpan={tableHeaders.length} message={t('no_quotations_found')} />
          ) : (
            quotations.map((quotation) => (
              <QuotationRow
                key={quotation.id}
                quotation={quotation}
                onSend={handleSend}
                onConvert={handleConvert}
                onDecline={handleDeclineRequest}
                onDelete={handleDeleteRequest}
                onDownload={handleDownload}
                sendingId={sendingId}
                t={t}
              />
            ))
          )}
        </M3Table>
      )}

      {!loading && !error && totalPages > 1 && (
        <nav aria-label={t('common:pagination')} className="flex items-center justify-center gap-3">
          <M3Button variant="outlined" icon="chevron_left" disabled={page === 0} onClick={handlePrevPage} aria-label={t('common:prev_page')}>
            {t('common:prev_page')}
          </M3Button>
          <span className="text-sm font-body text-on-surface-variant">
            {t('common:page_x_of_y', { current: page + 1, total: totalPages })}
          </span>
          <M3Button variant="outlined" icon="chevron_right" disabled={page >= totalPages - 1} onClick={handleNextPage} aria-label={t('common:next_page')}>
            {t('common:next_page')}
          </M3Button>
        </nav>
      )}

      {declineTarget && (
        <M3ConfirmDialog
          title={t('action_decline')}
          titleId="confirm-decline-quotation-dialog"
          message={t('confirm_decline')}
          onConfirm={handleDeclineConfirm}
          onCancel={handleDeclineClose}
          loading={busy}
        />
      )}

      {deleteTarget && (
        <M3ConfirmDialog
          title={t('action_delete')}
          titleId="confirm-delete-quotation-dialog"
          message={t('confirm_delete')}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteClose}
          loading={busy}
        />
      )}
    </div>
  );
};
