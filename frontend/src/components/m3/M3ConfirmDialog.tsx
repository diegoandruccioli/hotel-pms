import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Button } from './M3Button';
import { M3Dialog } from './M3Dialog';

interface M3ConfirmDialogProps {
  /** Callers that mount the dialog conditionally can leave this at its default. */
  open?: boolean;
  title: string;
  titleId?: string;
  message: ReactNode;
  /** Defaults to the common `confirm` label. */
  confirmLabel?: string;
  /** Defaults to the common `cancel` label. */
  cancelLabel?: string;
  onConfirm: () => void;
  /** Cancel button, header close button, Escape and scrim click all land here. */
  onCancel: () => void;
  /** While the confirmed action is running: cancel is disabled and confirm shows a spinner. */
  loading?: boolean;
}

/**
 * Replaces the `M3Dialog` + paragraph + Cancel/Confirm row copied into ~10
 * pages. For a confirmation that needs more than a message and two buttons
 * (a result list, a second step), use `M3Dialog` directly.
 */
export const M3ConfirmDialog = ({
  open = true,
  title,
  titleId,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
  loading = false,
}: M3ConfirmDialogProps) => {
  const { t } = useTranslation('common');

  return (
    <M3Dialog open={open} title={title} titleId={titleId} onClose={onCancel}>
      <p className="text-sm font-body text-on-surface">{message}</p>
      <div className="flex justify-end gap-3 pt-4">
        <M3Button type="button" variant="outlined" onClick={onCancel} disabled={loading}>
          {cancelLabel ?? t('cancel')}
        </M3Button>
        <M3Button type="button" onClick={onConfirm} loading={loading}>
          {confirmLabel ?? t('confirm')}
        </M3Button>
      </div>
    </M3Dialog>
  );
};
