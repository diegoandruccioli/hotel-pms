import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Dialog } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3Select } from '../../components/m3';
import { useToastStore } from '../../store';
import { stayService } from '../../services';
import { inventoryService } from '../../services';
import { getErrorMessage } from '../../utils';
import type { StayResponse } from '../../types';
import type { RoomResponse } from '../../types';

interface StayRoomChangeDialogProps {
  /** null closes the dialog. */
  stay: StayResponse | null;
  onClose: () => void;
  onChanged: (updated: StayResponse) => void;
}

const todayIso = (): string => new Date().toISOString().slice(0, 10);

/**
 * Moves an already checked-in stay to a different room (Parte 6), effective
 * today, via PUT /stays/{id}/room. The backend re-validates everything shown
 * here (destination must be CLEAN, have capacity for every guest still
 * present, and be free for the remaining nights) — this dialog's own
 * filtering is a convenience so the picker doesn't offer an obviously
 * invalid room, not a substitute for that check.
 */
export const StayRoomChangeDialog = memo(({ stay, onClose, onChanged }: StayRoomChangeDialogProps) => {
  const { t } = useTranslation('common');
  const addToast = useToastStore((s) => s.addToast);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loadingRooms, setLoadingRooms] = useState(false);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeGuestCount = useMemo(
    () => stay?.guests?.filter((g) => !g.departureDate).length ?? 0,
    [stay],
  );

  useEffect(() => {
    setError(null);
    setSelectedRoomId('');
    setRooms([]);
    if (!stay?.expectedCheckOutDate) return;

    // A stay overdue for check-out (expectedCheckOutDate already in the past —
    // nobody extended it, the guest hasn't left) has no valid [today, checkOut)
    // window to search: checked client-side first so the operator sees the
    // actionable reason immediately, instead of the generic "checkout must be
    // after checkin" the rooms/availability search would otherwise surface.
    if (stay.expectedCheckOutDate <= todayIso()) {
      setError(t('errors:STAY_ROOM_CHANGE_CHECKOUT_IN_PAST'));
      return;
    }

    let cancelled = false;
    setLoadingRooms(true);
    inventoryService
      .getAvailableRooms(todayIso(), stay.expectedCheckOutDate)
      .then((available) => {
        if (cancelled) return;
        setRooms(
          available.filter(
            // status === 'CLEAN': unlike a future-dated reservation search (where
            // today's housekeeping status is irrelevant, see inventoryService
            // .getAvailableRooms's own doc comment), THIS is an immediate move —
            // there is no cleaning-window buffer before the guest needs the room,
            // so DIRTY must be excluded here even though the endpoint itself
            // returns it. The backend enforces the same rule; this only saves the
            // operator from picking a room the confirm step would just reject.
            (r) => r.id !== stay.roomId && r.status === 'CLEAN'
              && r.roomType.maxOccupancy >= activeGuestCount,
          ),
        );
      })
      .catch((err: unknown) => {
        // A stay overdue for check-out (expectedCheckOutDate already in the past)
        // has no valid date range to search — surface the real reason instead of
        // silently falling through to "no rooms available", which would read as
        // "every room is occupied" rather than "extend the stay first".
        if (!cancelled) {
          setRooms([]);
          setError(getErrorMessage(err, t('stay_room_change_failed')));
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingRooms(false);
      });

    return () => {
      cancelled = true;
    };
    // activeGuestCount is derived from `stay` itself — including it would just
    // re-run this effect a second time for the same stay.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stay]);

  const roomOptions = useMemo(
    () => rooms.map((r) => ({
      value: r.id,
      label: `${r.roomNumber}${r.roomType?.name ? ` — ${r.roomType.name}` : ''}`,
    })),
    [rooms],
  );

  const handleRoomChange = useCallback(
    (e: ChangeEvent<HTMLSelectElement>) => setSelectedRoomId(e.target.value),
    [],
  );

  const handleConfirm = useCallback(async () => {
    if (!stay || !selectedRoomId) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await stayService.changeRoom(stay.id, selectedRoomId, stay.version);
      addToast(t('stay_room_change_success'), 'success');
      onChanged(updated);
      onClose();
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('stay_room_change_failed')));
    } finally {
      setSaving(false);
    }
  }, [stay, selectedRoomId, addToast, t, onChanged, onClose]);

  if (!stay) return null;

  return (
    <M3Dialog open={!!stay} title={t('stay_room_change_title')} titleId="stay-room-change-title" onClose={onClose}>
      <div className="space-y-4">
        <p className="text-sm text-on-surface-variant">
          {t('stay_room_change_current_room', { room: stay.roomNumber ?? '-' })}
        </p>
        <M3Select
          label={t('stay_room_change_select_room')}
          options={roomOptions}
          value={selectedRoomId}
          onChange={handleRoomChange}
          disabled={loadingRooms || roomOptions.length === 0}
          placeholder={
            loadingRooms
              ? t('stay_room_change_loading_rooms')
              : roomOptions.length === 0
                ? t('stay_room_change_no_rooms')
                // A real placeholder option (not `undefined`) even when rooms ARE
                // available: without one, a native <select> defaults to the first
                // <option> in the DOM regardless of React's controlled `value=""`,
                // silently "selecting" a room the operator never chose.
                : t('stay_room_change_choose_room')
          }
          required
        />
        {error && <p className="text-sm text-error">{error}</p>}
        <div className="flex justify-end gap-2">
          <M3Button variant="text" onClick={onClose} type="button">
            {t('cancel')}
          </M3Button>
          <M3Button
            onClick={handleConfirm}
            loading={saving}
            disabled={saving || !selectedRoomId}
            type="button"
          >
            {t('confirm')}
          </M3Button>
        </div>
      </div>
    </M3Dialog>
  );
});
StayRoomChangeDialog.displayName = 'StayRoomChangeDialog';
