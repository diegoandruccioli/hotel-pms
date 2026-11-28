import { useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { QuotationResponse } from '../../types';
import { Alert } from '../../components/Alert';
import { M3Button, M3Dialog } from '../../components/m3';
import { useGuestById } from '../../hooks/queries';

interface QuotationSendDialogProps {
  quotation: QuotationResponse;
  onConfirm: () => void;
  onCancel: () => void;
  /** While the send request runs: cancel is disabled and confirm shows a spinner. */
  loading?: boolean;
}

const NOOP = () => {};

/**
 * Asks for confirmation before a quotation is mailed and shows who gets it: an email cannot be
 * recalled, and a send also starts the per-recipient resend cooldown. A prospect's address is
 * already on the quotation; a registered guest's is fetched only while this dialog is open, so it
 * never travels with every row of the list.
 */
export const QuotationSendDialog = ({ quotation, onConfirm, onCancel, loading = false }: QuotationSendDialogProps) => {
  const { t } = useTranslation(['quotations', 'common']);
  const isResend = quotation.status === 'SENT';
  const guestId = quotation.guestId;
  const guest = useGuestById(guestId);
  const guestLinkState = useMemo(() => ({ guestId }), [guestId]);

  const email = guestId ? guest.data?.email?.trim() : quotation.prospectEmail?.trim();
  const lookingUp = !!guestId && guest.isPending;
  const lookupFailed = !!guestId && guest.isError;
  const { refetch: refetchGuest } = guest;
  const retryLookup = useCallback(() => {
    void refetchGuest();
  }, [refetchGuest]);
  const missingEmail = !lookingUp && !lookupFailed && !email;

  const footer = (
    <div className="flex justify-end gap-3">
      <M3Button type="button" variant="outlined" onClick={onCancel} disabled={loading}>
        {t('common:cancel')}
      </M3Button>
      <M3Button type="button" onClick={onConfirm} loading={loading} disabled={!email}>
        {isResend ? t('send_dialog_confirm_resend') : t('send_dialog_confirm')}
      </M3Button>
    </div>
  );

  return (
    <M3Dialog
      open
      title={isResend ? t('send_dialog_title_resend') : t('send_dialog_title')}
      titleId="confirm-send-quotation-dialog"
      onClose={loading ? NOOP : onCancel}
      footer={footer}
    >
      {lookingUp && <p className="text-sm font-body text-on-surface-variant">{t('send_dialog_loading')}</p>}

      {lookupFailed && (
        <Alert tone="warning" retryLabel={t('common:try_again')} onRetry={retryLookup}>
          {t('send_dialog_guest_unreadable')}
        </Alert>
      )}

      {missingEmail && (
        <Alert
          tone="warning"
          action={
            guestId ? (
              <Link
                to="/guests"
                state={guestLinkState}
                className="inline-flex min-h-11 items-center text-sm font-medium text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs"
              >
                {t('send_dialog_open_guest')}
              </Link>
            ) : undefined
          }
        >
          {t('send_dialog_no_email', { name: quotation.guestFullName })}
        </Alert>
      )}

      {email && (
        <div className="space-y-3 text-sm font-body text-on-surface">
          <p>{t('send_dialog_intro')}</p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
            <dt className="text-on-surface-variant">{t('send_dialog_recipient')}</dt>
            <dd className="font-medium break-words">{quotation.guestFullName}</dd>
            <dt className="text-on-surface-variant">{t('send_dialog_email')}</dt>
            <dd className="font-medium break-all">{email}</dd>
          </dl>
          <p className="text-on-surface-variant">{t('send_dialog_cooldown')}</p>
        </div>
      )}
    </M3Dialog>
  );
};
