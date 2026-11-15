import { useFormatters, useListRangeSummary } from '../hooks';
import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { quotationService } from '../services';
import type { QuotationResponse } from '../types';
import { M3LoadingState } from '../components/m3';
import { M3EmptyState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { PageHeader } from '../components/PageHeader';
import { M3Button } from '../components/m3';
import { M3Pagination } from '../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../components/m3';
import { M3RowActionsMenu, M3TableActionLink, type RowAction } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3ConfirmDialog } from '../components/m3';
import { useToastStore } from '../store';
import { getErrorMessage, quotationStatusTone } from '../utils';
import { quotationListParamsToSearch, readQuotationListParams } from './Quotations/quotationListParams';
import { QuotationSendDialog } from './Quotations/QuotationSendDialog';

const PAGE_SIZE = 20;

// Below `md` only the guest and the row actions stay as columns: the dates, the total and the status
// move under the guest's name (see `QuotationRow`), so nothing needs a sideways scroll on a phone.
const HIDE_DETAIL_COLUMNS_ON_PHONE =
  'max-md:[&_th:not(:first-child):not(:last-child)]:hidden max-md:[&_td:not(:first-child):not(:last-child):not([colspan])]:hidden';

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

type TFn = (key: string, opts?: Record<string, unknown>) => string;

const StatusChips = ({ quotation, t }: { quotation: QuotationResponse; t: TFn }) => (
  <div className="flex flex-col items-start gap-1">
    <M3StatusChip label={t(`status_${quotation.status.toLowerCase()}`)} tone={quotationStatusTone[quotation.status]} />
    {quotation.sendFailed && (
      <M3StatusChip label={t('send_failed_badge')} tone="error" />
    )}
  </div>
);

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
  const { formatCurrency, formatDate } = useFormatters();
  const navigate = useNavigate();
  const handleSend = useCallback(() => onSend(quotation.id), [onSend, quotation.id]);
  const handleConvert = useCallback(() => onConvert(quotation.id), [onConvert, quotation.id]);
  const handleDecline = useCallback(() => onDecline(quotation.id), [onDecline, quotation.id]);
  const handleDelete = useCallback(() => onDelete(quotation.id), [onDelete, quotation.id]);
  const handleDownload = useCallback(() => onDownload(quotation.id), [onDownload, quotation.id]);

  const canSend = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canConvert = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canDecline = quotation.status === 'DRAFT' || quotation.status === 'SENT';

  const reservationId = quotation.reservationId;
  const handleOpenReservation = useCallback(() => navigate(`/reservations/${reservationId}`), [navigate, reservationId]);

  const items = useMemo<RowAction[]>(() => {
    const list: RowAction[] = [];
    if (reservationId) list.push({ id: 'open-reservation', label: t('action_open_reservation'), icon: 'event_available', onSelect: handleOpenReservation });
    if (canConvert) list.push({ id: 'convert', label: t('action_convert'), icon: 'transform', onSelect: handleConvert });
    list.push({ id: 'download', label: t('action_download_pdf'), icon: 'download', onSelect: handleDownload });
    if (canDecline) list.push({ id: 'decline', label: t('action_decline'), icon: 'block', onSelect: handleDecline });
    list.push({ id: 'delete', label: t('action_delete'), icon: 'delete', tone: 'error', onSelect: handleDelete });
    return list;
  }, [reservationId, canConvert, canDecline, t, handleOpenReservation, handleConvert, handleDownload, handleDecline, handleDelete]);

  return (
    <M3TableRow>
      <M3TableCell className="font-medium">
        <Link to={`/quotations/${quotation.id}`} className="inline-flex min-h-11 items-center hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs">
          {quotation.guestFullName}
        </Link>
        <div className="flex flex-col gap-1.5 pb-1 font-normal whitespace-normal text-on-surface-variant md:hidden">
          <span>{formatDate(quotation.checkInDate)} – {formatDate(quotation.checkOutDate)}</span>
          <span className="font-medium">{formatTotal(quotation, t, formatCurrency)}</span>
          <StatusChips quotation={quotation} t={t} />
        </div>
      </M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(quotation.checkInDate)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(quotation.checkOutDate)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant font-medium">{formatTotal(quotation, t, formatCurrency)}</M3TableCell>
      <M3TableCell className="text-on-surface-variant">{formatDate(quotation.validUntil)}</M3TableCell>
      <M3TableCell>
        <StatusChips quotation={quotation} t={t} />
      </M3TableCell>
      <M3TableCell>
        <div className="flex items-center justify-end gap-1">
          {canSend && (
            <M3TableActionLink onClick={handleSend} disabled={sendingId === quotation.id}>
              {quotation.status === 'SENT' ? t('action_resend') : t('action_send')}
            </M3TableActionLink>
          )}
          <M3RowActionsMenu label={t('row_actions_for', { ns: 'common', name: quotation.guestFullName })} items={items} />
        </div>
      </M3TableCell>
    </M3TableRow>
  );
});
QuotationRow.displayName = 'QuotationRow';

export const Quotations = () => {
  const { t } = useTranslation(['quotations', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const { formatCurrency, formatDate } = useFormatters();

  // The page lives in the URL, so a refresh or Back from a detail comes back to the same place.
  const [urlParams, setUrlParams] = useSearchParams();
  const { page } = readQuotationListParams(urlParams);
  const [quotations, setQuotations] = useState<QuotationResponse[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sendingId, setSendingId] = useState<string | null>(null);
  const [sendTarget, setSendTarget] = useState<string | null>(null);
  const [declineTarget, setDeclineTarget] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [convertTarget, setConvertTarget] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadQuotations = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await quotationService.getAllQuotations(page, PAGE_SIZE);
      if (data.content.length === 0 && page > 0) {
        // The last row of the last page went away (or the URL names a page that does not exist).
        setUrlParams(quotationListParamsToSearch({ page: Math.max(0, data.totalPages - 1) }), { replace: true });
        return;
      }
      setQuotations(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('error_loading_quotations')));
    } finally {
      setLoading(false);
    }
  }, [page, t, setUrlParams]);

  useEffect(() => {
    loadQuotations();
  }, [loadQuotations]);

  const summary = useListRangeSummary(page, PAGE_SIZE, quotations.length, totalElements);

  const handleNew = useCallback(() => navigate('/quotations/new'), [navigate]);
  const goToPage = useCallback((next: number) => setUrlParams(quotationListParamsToSearch({ page: next })), [setUrlParams]);
  const handlePrevPage = useCallback(() => goToPage(page - 1), [goToPage, page]);
  const handleNextPage = useCallback(() => goToPage(page + 1), [goToPage, page]);
  const pageOfLabel = useCallback(
    (current: number, total: number) => t('common:page_x_of_y', { current, total }),
    [t],
  );

  // "Send" only opens the confirmation: the dialog shows who will receive the email.
  const handleSendRequest = useCallback((id: string) => setSendTarget(id), []);
  const handleSendClose = useCallback(() => setSendTarget(null), []);
  const handleSendConfirm = useCallback(async () => {
    if (!sendTarget) return;
    const id = sendTarget;
    setSendingId(id);
    try {
      const updated = await quotationService.sendQuotation(id);
      setQuotations((prev) => prev.map((q) => (q.id === id ? updated : q)));
      addToast(updated.sendFailed ? t('toast_send_failed') : t('toast_sent'), updated.sendFailed ? 'error' : 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_send_failed')), 'error');
    } finally {
      setSendingId(null);
      setSendTarget(null);
    }
  }, [sendTarget, addToast, t]);

  const handleConvert = useCallback(async (id: string) => {
    const target = quotations.find((q) => q.id === id);
    if (target && target.options.length > 1) {
      navigate(`/quotations/${id}`, { state: { openConvert: true } });
      return;
    }
    // One option: creating a reservation is not undone with a click, so it asks first.
    setConvertTarget(id);
  }, [quotations, navigate]);

  const handleConvertClose = useCallback(() => setConvertTarget(null), []);
  const handleConvertConfirm = useCallback(async () => {
    if (!convertTarget) return;
    setBusy(true);
    try {
      const reservation = await quotationService.convertToReservation(convertTarget);
      addToast(t('toast_converted'), 'success');
      navigate(`/reservations/${reservation.id}`);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_convert')), 'error');
    } finally {
      setBusy(false);
      setConvertTarget(null);
    }
  }, [convertTarget, addToast, t, navigate]);

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
      addToast(getErrorMessage(err, t('err_decline')), 'error');
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
      addToast(getErrorMessage(err, t('err_delete')), 'error');
    } finally {
      setBusy(false);
      setDeleteTarget(null);
    }
  }, [deleteTarget, addToast, t, loadQuotations]);

  const sendQuotationTarget = sendTarget ? quotations.find((q) => q.id === sendTarget) : undefined;
  const declineQuotation = declineTarget ? quotations.find((q) => q.id === declineTarget) : undefined;
  const deleteQuotationTarget = deleteTarget ? quotations.find((q) => q.id === deleteTarget) : undefined;
  const convertQuotation = convertTarget ? quotations.find((q) => q.id === convertTarget) : undefined;
  const summaryOpts = (q: QuotationResponse) => ({
    name: q.guestFullName,
    from: formatDate(q.checkInDate),
    to: formatDate(q.checkOutDate),
  });

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
      ) : quotations.length === 0 ? (
        <M3EmptyState
          icon="request_quote"
          title={t('empty_title')}
          description={t('empty_hint')}
          action={<M3Button icon="add" onClick={handleNew}>{t('new_quotation')}</M3Button>}
        />
      ) : (
        <M3Table headers={tableHeaders} stickyEnd className={HIDE_DETAIL_COLUMNS_ON_PHONE}>
          {quotations.map((quotation) => (
            <QuotationRow
              key={quotation.id}
              quotation={quotation}
              onSend={handleSendRequest}
              onConvert={handleConvert}
              onDecline={handleDeclineRequest}
              onDelete={handleDeleteRequest}
              onDownload={handleDownload}
              sendingId={sendingId}
              t={t}
            />
          ))}
        </M3Table>
      )}

      {!loading && !error && (
        <M3Pagination
          page={page}
          totalPages={totalPages}
          onPrev={handlePrevPage}
          onNext={handleNextPage}
          pageLabel={t('common:pagination')}
          prevLabel={t('common:prev_page')}
          nextLabel={t('common:next_page')}
          pageOfLabel={pageOfLabel}
          summary={summary}
        />
      )}

      {sendQuotationTarget && (
        <QuotationSendDialog
          quotation={sendQuotationTarget}
          onConfirm={handleSendConfirm}
          onCancel={handleSendClose}
          loading={sendingId === sendQuotationTarget.id}
        />
      )}

      {declineQuotation && (
        <M3ConfirmDialog
          title={t('list_decline_title')}
          titleId="confirm-decline-quotation-dialog"
          message={t('list_decline_message', summaryOpts(declineQuotation))}
          confirmLabel={t('list_decline_confirm')}
          onConfirm={handleDeclineConfirm}
          onCancel={handleDeclineClose}
          loading={busy}
        />
      )}

      {deleteQuotationTarget && (
        <M3ConfirmDialog
          title={t('list_delete_title')}
          titleId="confirm-delete-quotation-dialog"
          message={t('list_delete_message', summaryOpts(deleteQuotationTarget))}
          confirmLabel={t('list_delete_confirm')}
          onConfirm={handleDeleteConfirm}
          onCancel={handleDeleteClose}
          loading={busy}
        />
      )}

      {convertQuotation && (
        <M3ConfirmDialog
          title={t('list_convert_title')}
          titleId="confirm-convert-quotation-dialog"
          message={t('list_convert_message', { ...summaryOpts(convertQuotation), total: formatCurrency(convertQuotation.totalPrice) })}
          confirmLabel={t('list_convert_confirm')}
          onConfirm={handleConvertConfirm}
          onCancel={handleConvertClose}
          loading={busy}
        />
      )}
    </div>
  );
};
