import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { M3ConfirmDialog } from '../components/m3';

/**
 * Asks before a dialog is dismissed with unsaved edits (R9). Escape, the scrim, the close button
 * and a Cancel button all have to go through `requestClose`; render `discardDialog` next to the
 * dialog. A save that succeeds must call the real `onClose`, not `requestClose`.
 */
export function useDiscardGuard(dirty: boolean, subject: string, onClose: () => void) {
  const { t } = useTranslation('common');
  const [asking, setAsking] = useState(false);

  const requestClose = useCallback(() => {
    if (dirty) setAsking(true);
    else onClose();
  }, [dirty, onClose]);

  const handleKeepEditing = useCallback(() => setAsking(false), []);
  const handleLeave = useCallback(() => {
    setAsking(false);
    onClose();
  }, [onClose]);

  const discardDialog = asking ? (
    <M3ConfirmDialog
      title={t('unsaved_title')}
      message={subject}
      cancelLabel={t('unsaved_keep_editing')}
      confirmLabel={t('unsaved_leave')}
      onCancel={handleKeepEditing}
      onConfirm={handleLeave}
    />
  ) : null;

  return { requestClose, discardDialog };
}
