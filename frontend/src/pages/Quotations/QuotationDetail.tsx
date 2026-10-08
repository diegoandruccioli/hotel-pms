import { useFormatters } from '../../hooks';
import { useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuotation } from '../../hooks/queries';
import { Alert } from '../../components/Alert';
import { M3LoadingState } from '../../components/m3';
import { M3ErrorState } from '../../components/m3';
import { PageHeader } from '../../components/PageHeader';
import { M3Button } from '../../components/m3';
import { M3Card } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3ConfirmDialog } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { QuotationPdfPreviewDialog } from './QuotationPdfPreviewDialog';
import { QuotationOptionCard } from './QuotationOptionCard';
import { useQuotationDetailActions } from './useQuotationDetailActions';
import { getErrorMessage, cn, quotationStatusTone } from '../../utils';

export const QuotationDetail = () => {
  const { formatCurrency, formatDate } = useFormatters();
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation(['quotations', 'common']);
  const navigate = useNavigate();

  const quotationQuery = useQuotation(id);
  const quotation = quotationQuery.data;
  const loading = quotationQuery.isLoading;
  const error = quotationQuery.error ? getErrorMessage(quotationQuery.error, t('error_loading_quotation')) : null;
  const { refetch } = quotationQuery;
  const loadQuotation = useCallback(() => { void refetch(); }, [refetch]);

  const handleBack = useCallback(() => navigate('/quotations'), [navigate]);
  const {
    busy, previewOpen, declineConfirmOpen, deleteConfirmOpen, convertDialogOpen, convertChoiceId, setConvertChoiceId,
    handleEdit, openPreview, closePreview, handleDownload, handleSend, handleConvertClick, closeConvertDialog,
    confirmConvert, handleDuplicate, openDeclineConfirm, closeDeclineConfirm, handleDeclineConfirmed,
    openDeleteConfirm, closeDeleteConfirm, handleDeleteConfirmed,
  } = useQuotationDetailActions(id, quotation);

  const sortedOptions = useMemo(
    () => (quotation ? [...quotation.options].sort((a, b) => a.position - b.position) : []),
    [quotation],
  );

  const lineItemHeaders = useMemo(
    () => [t('col_room'), t('col_room_type'), t('col_price')],
    [t],
  );

  if (loading) {
    return (
      <M3LoadingState label={t('common:loading')} plain />
    );
  }

  if (error || !quotation) {
    return (
      <M3ErrorState
        title={t('error_loading_quotation')}
        message={error ?? ''}
        retryLabel={t('common:try_again')}
        onRetry={loadQuotation}
      />
    );
  }

  const canSend = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canConvert = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canDecline = quotation.status === 'DRAFT' || quotation.status === 'SENT';
  const canEdit = quotation.status === 'DRAFT';

  return (
    <div className="space-y-6 max-w-4xl mx-auto pb-10">
      <PageHeader
        title={quotation.guestFullName}
        crumbLabel={quotation.guestFullName}
        titleAdornment={
          <M3StatusChip label={t(`status_${quotation.status.toLowerCase()}`)} tone={quotationStatusTone[quotation.status]} />
        }
        onBack={handleBack}
        bordered
      />

      {quotation.sendFailed && (
        <Alert tone="error">{t('send_failed_banner')}</Alert>
      )}

      <M3Card variant="solid" className="p-6 space-y-4">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_check_in')}</p>
            <p className="text-sm font-medium text-on-surface">{formatDate(quotation.checkInDate)}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_check_out')}</p>
            <p className="text-sm font-medium text-on-surface">{formatDate(quotation.checkOutDate)}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('label_expected_guests')}</p>
            <p className="text-sm font-medium text-on-surface">{quotation.expectedGuests ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs font-body text-on-surface-variant">{t('col_valid_until')}</p>
            <p className={cn('text-sm font-medium', quotation.status === 'EXPIRED' ? 'text-error' : 'text-on-surface')}>
              {formatDate(quotation.validUntil)}
            </p>
          </div>
        </div>

        {sortedOptions.length > 1 ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {sortedOptions.map((option) => (
              <QuotationOptionCard
                key={option.id}
                option={option}
                isAccepted={quotation.acceptedOptionId === option.id}
                isConvertChoice={false}
                selectable={false}
              />
            ))}
          </div>
        ) : sortedOptions[0] ? (
          <M3Table headers={lineItemHeaders}>
            {sortedOptions[0].lineItems.map((li) => (
              <M3TableRow key={li.id}>
                <M3TableCell className="font-medium">{li.roomNumber}</M3TableCell>
                <M3TableCell className="text-on-surface-variant">{li.roomTypeName}</M3TableCell>
                <M3TableCell className="text-on-surface-variant">{formatCurrency(li.price)}</M3TableCell>
              </M3TableRow>
            ))}
          </M3Table>
        ) : null}

        <p className="text-right text-lg font-medium text-on-surface">
          {t('quotation_total', { amount: formatCurrency(quotation.totalPrice) })}
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
          <M3Button variant="outlined" icon="check_circle" onClick={handleConvertClick} loading={busy} disabled={busy}>
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
          className="text-error hover:bg-error/8"
        >
          {t('action_delete')}
        </M3Button>
      </div>

      {previewOpen && id && <QuotationPdfPreviewDialog quotationId={id} onClose={closePreview} />}

      {convertDialogOpen && (
        <M3Dialog open title={t('action_convert')} titleId="convert-quotation-choose-option-dialog" onClose={closeConvertDialog}>
          <p className="text-sm font-body text-on-surface mb-4">{t('label_choose_option_to_convert')}</p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {sortedOptions.map((option) => (
              <QuotationOptionCard
                key={option.id}
                option={option}
                isAccepted={quotation.acceptedOptionId === option.id}
                isConvertChoice={convertChoiceId === option.id}
                selectable
                onChoose={setConvertChoiceId}
              />
            ))}
          </div>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={closeConvertDialog} disabled={busy}>{t('common:cancel')}</M3Button>
            <M3Button type="button" onClick={confirmConvert} loading={busy} disabled={busy || !convertChoiceId}>
              {t('common:confirm')}
            </M3Button>
          </div>
        </M3Dialog>
      )}

      {declineConfirmOpen && (
        <M3ConfirmDialog
          title={t('action_decline')}
          titleId="confirm-decline-quotation-detail-dialog"
          message={t('confirm_decline')}
          onConfirm={handleDeclineConfirmed}
          onCancel={closeDeclineConfirm}
          loading={busy}
        />
      )}

      {deleteConfirmOpen && (
        <M3ConfirmDialog
          title={t('action_delete')}
          titleId="confirm-delete-quotation-detail-dialog"
          message={t('confirm_delete')}
          onConfirm={handleDeleteConfirmed}
          onCancel={closeDeleteConfirm}
          loading={busy}
        />
      )}
    </div>
  );
};
