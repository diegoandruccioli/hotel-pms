import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { quotationService } from '../../services';
import type { QuotationResponse } from '../../types';
import { useToastStore } from '../../store';
import { queryKeys } from '../../lib';
import { getErrorMessage } from '../../utils';

/** Header actions of the quotation detail page: send, convert, duplicate, decline, delete, PDF, and their dialog/busy state. */
export function useQuotationDetailActions(id: string | undefined, quotation: QuotationResponse | undefined) {
  const { t } = useTranslation(['quotations', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const [busy, setBusy] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [declineConfirmOpen, setDeclineConfirmOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [convertDialogOpen, setConvertDialogOpen] = useState(false);
  const [convertChoiceId, setConvertChoiceId] = useState<string | null>(null);

  const handleEdit = useCallback(() => navigate(`/quotations/${id}/edit`), [navigate, id]);
  const openPreview = useCallback(() => setPreviewOpen(true), []);
  const closePreview = useCallback(() => setPreviewOpen(false), []);
  const handleDownload = useCallback(async () => {
    if (!id) return;
    try {
      await quotationService.downloadPdf(id);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('download_failed', { ns: 'common' })), 'error');
    }
  }, [id, addToast, t]);

  const handleSend = useCallback(async () => {
    if (!id) return;
    setBusy(true);
    try {
      const updated = await quotationService.sendQuotation(id);
      queryClient.setQueryData(queryKeys.quotations.detail(id), updated);
      addToast(updated.sendFailed ? t('toast_send_failed') : t('toast_sent'), updated.sendFailed ? 'error' : 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_send_failed')), 'error');
    } finally {
      setBusy(false);
    }
  }, [id, addToast, t, queryClient]);

  const performConvert = useCallback(async (optionId: string | null) => {
    if (!id) return;
    setBusy(true);
    try {
      const reservation = await quotationService.convertToReservation(id, optionId ?? undefined);
      addToast(t('toast_converted'), 'success');
      navigate(`/reservations/${reservation.id}`);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_converted')), 'error');
    } finally {
      setBusy(false);
      setConvertDialogOpen(false);
    }
  }, [id, addToast, t, navigate]);

  const handleConvertClick = useCallback(() => {
    if (!quotation) return;
    if (quotation.options.length === 1) {
      performConvert(quotation.options[0].id);
      return;
    }
    setConvertChoiceId(quotation.acceptedOptionId ?? quotation.options[0]?.id ?? null);
    setConvertDialogOpen(true);
  }, [quotation, performConvert]);

  const closeConvertDialog = useCallback(() => setConvertDialogOpen(false), []);
  const confirmConvert = useCallback(() => performConvert(convertChoiceId), [performConvert, convertChoiceId]);

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
      queryClient.setQueryData(queryKeys.quotations.detail(id), updated);
      addToast(t('toast_declined'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('toast_declined')), 'error');
    } finally {
      setBusy(false);
      setDeclineConfirmOpen(false);
    }
  }, [id, addToast, t, queryClient]);

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

  return {
    busy, previewOpen, declineConfirmOpen, deleteConfirmOpen, convertDialogOpen, convertChoiceId, setConvertChoiceId,
    handleEdit, openPreview, closePreview, handleDownload, handleSend, handleConvertClick, closeConvertDialog, confirmConvert, handleDuplicate, openDeclineConfirm, closeDeclineConfirm, handleDeclineConfirmed, openDeleteConfirm, closeDeleteConfirm, handleDeleteConfirmed,
  };
}
