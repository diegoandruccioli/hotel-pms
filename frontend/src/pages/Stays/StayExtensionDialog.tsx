import { useState, useEffect, useCallback, memo } from 'react';
import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3/M3Dialog';
import { M3Button } from '../../components/m3/M3Button';
import { M3TextField } from '../../components/m3/M3TextField';
import { useToastStore } from '../../store/toastStore';
import { stayService } from '../../services/stayService';
import { getErrorMessage } from '../../utils/errorMessage';
import type { StayResponse } from '../../types';

interface StayExtensionDialogProps {
  /** null closes the dialog. */
  stay: StayResponse | null;
  onClose: () => void;
  onExtended: (updated: StayResponse) => void;
}

/**
 * Defaults the date picker to one night past the current check-out. Uses UTC
 * getters/setters throughout — an ISO date-only string parses as UTC midnight,
 * so mixing in local-timezone methods (getDate/setDate) would shift the result
 * by a day in any timezone west of UTC.
 */
const dayAfter = (isoDate: string): string => {
  const d = new Date(isoDate);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
};

/**
 * "I'm staying another night" at the desk (Parte 3) — extends expectedCheckOutDate
 * on an open stay via PUT /stays/{id}. The backend verifies room availability for
 * the added nights and posts the supplementary ROOM_NIGHT/CITY_TAX charges; this
 * dialog just collects the new date.
 */
export const StayExtensionDialog = memo(({ stay, onClose, onExtended }: StayExtensionDialogProps) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [newCheckOutDate, setNewCheckOutDate] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    setNewCheckOutDate(stay?.expectedCheckOutDate ? dayAfter(stay.expectedCheckOutDate) : '');
  }, [stay]);

  const handleDateChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => setNewCheckOutDate(e.target.value),
    [],
  );

  const handleConfirm = useCallback(async () => {
    if (!stay || !newCheckOutDate) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await stayService.extendStay(stay.id, newCheckOutDate, stay.version);
      addToast(t('stay_extension_success'), 'success');
      onExtended(updated);
      onClose();
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('stay_extension_failed')));
    } finally {
      setSaving(false);
    }
  }, [stay, newCheckOutDate, addToast, t, onExtended, onClose]);

  if (!stay) return null;

  return (
    <M3Dialog open={!!stay} title={t('stay_extension_title')} titleId="stay-extension-title" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-on-surface-variant">
          {t('stay_extension_current_checkout', { date: stay.expectedCheckOutDate ?? '-' })}
        </p>
        <M3TextField
          label={t('stay_extension_new_checkout')}
          type="date"
          value={newCheckOutDate}
          onChange={handleDateChange}
        />
        {error && <p className="text-sm text-error">{error}</p>}
        <div className="flex justify-end gap-2">
          <M3Button variant="text" onClick={onClose} type="button">
            {t('cancel')}
          </M3Button>
          <M3Button onClick={handleConfirm} loading={saving} disabled={saving || !newCheckOutDate} type="button">
            {t('confirm')}
          </M3Button>
        </div>
      </div>
    </M3Dialog>
  );
});
StayExtensionDialog.displayName = 'StayExtensionDialog';
