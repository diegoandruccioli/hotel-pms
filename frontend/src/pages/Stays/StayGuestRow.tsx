import { useCallback, memo } from 'react';
import { M3Button, M3StatusChip, M3TextField } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import type { StayGuestResponse } from '../../types';
import type { GuestErrorTranslator } from './stayGuestFieldHelpers';

interface StayGuestRowProps {
  guest: StayGuestResponse;
  t: GuestErrorTranslator;
  busyGuestId: string | null;
  departureTargetId: string | null;
  departureDate: string;
  onEdit: (guest: StayGuestResponse) => void;
  onStartDeparture: (id: string) => void;
  onCancelDeparture: () => void;
  onConfirmDeparture: () => void;
  onDepartureDateChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onPromote: (id: string) => void;
  onRequestRemove: (id: string) => void;
}

export const StayGuestRow = memo(({
  guest, t, busyGuestId, departureTargetId, departureDate,
  onEdit, onStartDeparture, onCancelDeparture, onConfirmDeparture, onDepartureDateChange,
  onPromote, onRequestRemove,
}: StayGuestRowProps) => {
  const isBusy = busyGuestId === guest.id;
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleStartDeparture = useCallback(() => onStartDeparture(guest.id), [onStartDeparture, guest.id]);
  const handlePromote = useCallback(() => onPromote(guest.id), [onPromote, guest.id]);
  const handleRequestRemove = useCallback(() => onRequestRemove(guest.id), [onRequestRemove, guest.id]);

  return (
    <div className="border border-outline-variant rounded-shape-md p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <MaterialIcon name="person" size={18} className="text-on-surface-variant" />
          <span className="font-medium text-on-surface">{guest.lastName} {guest.firstName}</span>
          {guest.isPrimaryGuest && <M3StatusChip label={t('guest_badge_primary')} tone="neutral" />}
          {guest.alloggiatiSent && <M3StatusChip label={t('guest_badge_sent')} tone="success" />}
          {guest.needsResubmit && <M3StatusChip label={t('guest_badge_needs_resubmit')} tone="error" />}
          {guest.departureDate && (
            <M3StatusChip label={t('guest_badge_departed', { date: guest.departureDate })} tone="neutral" />
          )}
        </div>
        <span className="text-xs text-on-surface-variant">
          {t('guest_arrival_date_label')}: {guest.arrivalDate}
        </span>
      </div>

      {departureTargetId === guest.id ? (
        <div className="flex items-end gap-2 mt-3">
          <M3TextField
            label={t('label_departure_date')}
            type="date"
            value={departureDate}
            onChange={onDepartureDateChange}
          />
          <M3Button variant="tonal" onClick={onConfirmDeparture} loading={isBusy} disabled={isBusy}>
            {t('btn_confirm')}
          </M3Button>
          <M3Button variant="text" onClick={onCancelDeparture}>
            {t('btn_cancel')}
          </M3Button>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2 mt-3">
          <M3Button variant="text" icon="edit" onClick={handleEdit}>
            {t('btn_edit')}
          </M3Button>
          {!guest.departureDate && (
            <M3Button variant="text" icon="logout" onClick={handleStartDeparture}>
              {t('btn_record_departure')}
            </M3Button>
          )}
          {!guest.isPrimaryGuest && (
            <M3Button variant="text" icon="star" onClick={handlePromote} loading={isBusy} disabled={isBusy}>
              {t('btn_promote_primary')}
            </M3Button>
          )}
          <M3Button
            variant="text"
            icon="delete"
            onClick={handleRequestRemove}
            disabled={guest.alloggiatiSent || guest.isPrimaryGuest}
            title={guest.alloggiatiSent
              ? t('hint_remove_disabled_sent')
              : guest.isPrimaryGuest
                ? t('hint_remove_disabled_primary')
                : undefined}
          >
            {t('btn_remove')}
          </M3Button>
        </div>
      )}
    </div>
  );
});
StayGuestRow.displayName = 'StayGuestRow';
