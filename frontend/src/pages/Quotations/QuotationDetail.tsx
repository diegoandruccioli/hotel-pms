import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { quotationService } from '../../services/quotationService';
import type { QuotationResponse, QuotationStatus } from '../../types/quotation.types';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3StatusChip } from '../../components/m3/M3StatusChip';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3/M3Table';
import { QuotationPdfPreviewDialog } from './QuotationPdfPreviewDialog';
import { useToastStore } from '../../store/toastStore';
import { getErrorMessage } from '../../utils/errorMessage';

const STATUS_TONE: Record<QuotationStatus, 'success' | 'warning' | 'error' | 'neutral' | 'info'> = {
  DRAFT: 'neutral',
  SENT: 'info',
  ACCEPTED: 'success',
  DECLINED: 'error',
  EXPIRED: 'warning',
};

export const QuotationDetail = () => {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation(['quotations', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);

  const [quotation, setQuotation] = useState<QuotationResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [declineConfirmOpen, setDeclineConfirmOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  const loadQuotation = useCallback(async () => {
    if (!id) return;
    try {
      setLoading(true);
      setError(null);
      const data = await quotationService.getQuotationById(id);
      setQuotation(data);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('error_loading_quotation')));
    } finally {
      setLoading(false);
    }
  }, [id, t]);

  useEffect(() => {
    loadQuotation();
  }, [loadQuotation]);

  const handleBack = useCallback(() => navigate('/quotations'), [navigate]);
  const handleEdit = useCallback(() => navigate(`/quotations/${id}/edit`), [navigate, id]);
  const openPreview = useCallback(() => setPreviewOpen(true), []);
  const closePreview = useCallback(() => setPreviewOpen(false), []);
  const handleDownload = useCallback(() => {
    if (id) quotationService.downloadPdf(id);
  }, [id]);

  const handleSend = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      const updated = await quotationService.sendQuotation(id);
      setQuotation(updated);
      addToast(updated.sendFailed ? t('toast_send_failed') : t('toast_sent'), updated.sendFailed ? 'error' : 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_send_failed')), 'error');
    } finally {
      setBusy(false);
    }
  }, [id, addToast, t]);

  const handleConvert = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      const reservation = await quotationService.convertToReservation(id);
      addToast(t('toast_converted'), 'success');
      navigate(`/reservations/${reservation.id}`);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_converted')), 'error');
    } finally {
      setBusy(false);
    }
  }, [id, addToast, t, navigate]);

  const handleDuplicate = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      const duplicate = await quotationService.duplicateQuotation(id);
      addToast(t('toast_duplicated'), 'success');
      navigate(`/quotations/${duplicate.id}`);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_duplicated')), 'error');
    } finally {
      setBusy(false);
    }
  }, [id, addToast, t, navigate]);

  const openDeclineConfirm = useCallback(() => setDeclineConfirmOpen(true), []);
  const closeDeclineConfirm = useCallback(() => setDeclineConfirmOpen(false), []);
  const handleDeclineConfirmed = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      const updated = await quotationService.declineQuotation(id);
      setQuotation(updated);
      addToast(t('toast_declined'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_declined')), 'error');
    } finally {
      setBusy(false);
      setDeclineConfirmOpen(false);
    }
  }, [id, addToast, t]);

  const openDeleteConfirm = useCallback(() => setDeleteConfirmOpen(true), []);
  const closeDeleteConfirm = useCallback(() => setDeleteConfirmOpen(false), []);
  const handleDeleteConfirmed = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      await quotationService.deleteQuotation(id);
      addToast(t('toast_deleted'), 'success');
      navigate('/quotations');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_deleted')), 'error');
      setBusy(false);
      setDeleteConfirmOpen(false);
    }
  }, [id, addToast, t, navigate]);

  const tableHeaders = useMemo(() => [
    t('col_room'), t('col_room_type'), t('col_price'),
  ], [t]);

  if (loading) {
    return (
      <div className="flex justify-center items-center h-64">
        <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
      </div>
    );
  }

  if (error || !quotation) {
    return (
      <div className="flex items-center gap-3 px-4 py-4 rounded-shape-sm bg-error-container text-on-error-container">
        <MaterialIcon name="error" size={20} className="flex-shrink-0" />
        <div>
          <h3 className="text-sm font-medium font-body">{t('error_loading_quotation')}</h3>
          <p className="mt-1 text-sm font-body opacity-80">{error}</p>
          <button type="button" onClick={loadQuotation} className="mt-2 text-sm font-medium underline hover:no-underline">
            {t('common:try_again')}
          </button>
        </div>
      </div>
    );
  }

  const canSend = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canConvert = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canDecline = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canEdit = quotation.status === 'DRAFT';

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-10">
      <div className="flex items-center gap-4 border-b border-outline-variant pb-4">
        <button
          type="button"
          onClick={handleBack}
          className="p-2 rounded-full hover:bg-surface-variant transition-colors text-on-surface-variant"
          aria-label={t('common:back')}
        >
          <MaterialIcon name="arrow_back" />
        </button>
        <div className="flex-1">
          <h1 className="text-2xl font-display font-bold text-on-surface flex items-center gap-3">
            {quotation.guestFullName}
            <M3StatusChip label={t(`status_${quotation.status.toLowerCase()}`)} tone={STATUS_TONE[quotation.status]} />
          </h1>
        </div>
      </div>

      {quotation.sendFailed && (
        <div className="flex items-center gap-3 px-4 py-3 rounded-shape-sm bg-error-container text-on-error-container">
          <MaterialIcon name="error" size={20} className="flex-shrink-0" />
          <p className="text-sm font-body">{t('send_failed_banner')}</p>
        </div>
      )}

      <M3Card className="p-6 space-y-4">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_check_in')}</p>
            <p className="text-sm font-medium text-on-surface">{quotation.checkInDate}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_check_out')}</p>
            <p className="text-sm font-medium text-on-surface">{quotation.checkOutDate}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('label_expected_guests')}</p>
            <p className="text-sm font-medium text-on-surface">{quotation.expectedGuests ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_valid_until')}</p>
            <p className={`text-sm font-medium ${quotation.status === 'EXPIRED' ? 'text-error' : 'text-on-surface'}`}>
              {quotation.validUntil}
            </p>
          </div>
        </div>

        <M3Table headers={tableHeaders}>
          {quotation.lineItems.map((li) => (
            <M3TableRow key={li.id}>
              <M3TableCell className="font-medium">{li.roomNumber}</M3TableCell>
              <M3TableCell className="text-on-surface-variant">{li.roomTypeName}</M3TableCell>
              <M3TableCell className="text-on-surface-variant">€ {li.price.toFixed(2)}</M3TableCell>
            </M3TableRow>
          ))}
        </M3Table>

        <p className="text-right text-lg font-medium text-on-surface">
          {t('quotation_total', { amount: `€ ${quotation.totalPrice.toFixed(2)}` })}
        </p>
      </M3Card>

      <div className="flex flex-wrap gap-2">
        {canSend && (
          <M3Button icon="send" onClick={handleSend} loading={busy} disabled={busy}>
            {quotation.status === 'SENT' ? t('action_resend') : t('action_send')}
          </M3Button>
        )}
        <M3Button variant="outlined" icon="visibility" onClick={openPreview} disabled={busy}>
          {t('action_preview_pdf')}
        </M3Button>
        <M3Button variant="outlined" icon="download" onClick={handleDownload} disabled={busy}>
          {t('action_download_pdf')}
        </M3Button>
        <M3Button variant="outlined" icon="content_copy" onClick={handleDuplicate} loading={busy} disabled={busy}>
          {t('action_duplicate')}
        </M3Button>
        {canEdit && (
          <M3Button variant="outlined" icon="edit" onClick={handleEdit} disabled={busy}>
            {t('common:edit')}
          </M3Button>
        )}
        {canConvert && (
          <M3Button variant="outlined" icon="check_circle" onClick={handleConvert} loading={busy} disabled={busy}>
            {t('action_convert')}
          </M3Button>
        )}
        {canDecline && (
          <M3Button variant="text" icon="cancel" onClick={openDeclineConfirm} disabled={busy}>
            {t('action_decline')}
          </M3Button>
        )}
        <M3Button
          variant="text"
          icon="delete"
          onClick={openDeleteConfirm}
          disabled={busy}
          className="text-error hover:bg-error/[0.08]"
        >
          {t('action_delete')}
        </M3Button>
      </div>

      {previewOpen && id && <QuotationPdfPreviewDialog quotationId={id} onClose={closePreview} />}

      {declineConfirmOpen && (
        <M3Dialog open title={t('action_decline')} titleId="confirm-decline-quotation-detail-dialog" onClose={closeDeclineConfirm}>
          <p className="text-sm font-body text-on-surface">{t('confirm_decline')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={closeDeclineConfirm} disabled={busy}>{t('common:cancel')}</M3Button>
            <M3Button type="button" onClick={handleDeclineConfirmed} loading={busy}>{t('common:confirm')}</M3Button>
          </div>
        </M3Dialog>
      )}

      {deleteConfirmOpen && (
        <M3Dialog open title={t('action_delete')} titleId="confirm-delete-quotation-detail-dialog" onClose={closeDeleteConfirm}>
          <p className="text-sm font-body text-on-surface">{t('confirm_delete')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={closeDeleteConfirm} disabled={busy}>{t('common:cancel')}</M3Button>
            <M3Button type="button" onClick={handleDeleteConfirmed} loading={busy}>{t('common:confirm')}</M3Button>
          </div>
        </M3Dialog>
      )}
    </div>
  );
};
