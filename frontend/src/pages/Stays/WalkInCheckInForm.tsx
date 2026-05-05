import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services/stayService';
import { guestService } from '../../services/guestService';
import type { AvailableRoom } from '../../types/stay.types';
import type { GuestResponseDTO } from '../../types/guest.types';
import { useToastStore } from '../../store/toastStore';

const todayIso = new Date().toISOString().split('T')[0];

// -----------------------------------------------------------------------
// GuestOption — isolates the onClick binding so the parent map is stable
// -----------------------------------------------------------------------

interface GuestOptionProps {
  guest: GuestResponseDTO;
  selected: boolean;
  onSelect: (g: GuestResponseDTO) => void;
}

const GuestOption = memo(({ guest, selected, onSelect }: GuestOptionProps) => {
  const handleClick = useCallback(() => onSelect(guest), [onSelect, guest]);
  return (
    <div role="option" aria-selected={selected}>
      <button type="button" onClick={handleClick}
        className="w-full text-left px-3 py-2 text-sm hover:bg-surface-variant focus:bg-surface-variant focus:outline-none">
        {guest.firstName} {guest.lastName}
        {guest.email ? ` · ${guest.email}` : ''}
      </button>
    </div>
  );
});
GuestOption.displayName = 'GuestOption';

// -----------------------------------------------------------------------
// WalkInCheckInForm
// -----------------------------------------------------------------------

export function WalkInCheckInForm() {
  const { t } = useTranslation('stays');
  const navigate = useNavigate();
  const { addToast } = useToastStore();

  const [rooms, setRooms] = useState<AvailableRoom[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState('');
  const [guestQuery, setGuestQuery] = useState('');
  const [guestResults, setGuestResults] = useState<GuestResponseDTO[]>([]);
  const [selectedGuest, setSelectedGuest] = useState<GuestResponseDTO | null>(null);
  const [expectedCheckOutDate, setExpectedCheckOutDate] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [roomsLoading, setRoomsLoading] = useState(true);

  useEffect(() => {
    setRoomsLoading(true);
    stayService
      .getAvailableRooms()
      .then(setRooms)
      .catch(() => setRooms([]))
      .finally(() => setRoomsLoading(false));
  }, []);

  const handleRoomChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    setSelectedRoomId(e.target.value);
  }, []);

  const handleGuestQueryChange = useCallback(async (e: React.ChangeEvent<HTMLInputElement>) => {
    const query = e.target.value;
    setGuestQuery(query);
    setSelectedGuest(null);
    if (query.trim().length < 2) {
      setGuestResults([]);
      return;
    }
    try {
      const results = await guestService.searchGuests(query);
      setGuestResults(results);
    } catch {
      setGuestResults([]);
    }
  }, []);

  const handleGuestSelect = useCallback((guest: GuestResponseDTO) => {
    setSelectedGuest(guest);
    setGuestQuery(`${guest.firstName} ${guest.lastName}`);
    setGuestResults([]);
  }, []);

  const handleCheckoutChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setExpectedCheckOutDate(e.target.value);
  }, []);

  const handleNavigateBack = useCallback(() => navigate('/stays'), [navigate]);

  const handleSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setError('');
      if (!selectedRoomId) { setError(t('walkin_err_room_required')); return; }
      if (!selectedGuest)   { setError(t('walkin_err_guest_required')); return; }
      if (!expectedCheckOutDate) { setError(t('walkin_err_checkout_required')); return; }

      setLoading(true);
      try {
        await stayService.createStay({
          guestId: selectedGuest.id,
          roomId: selectedRoomId,
          status: 'CHECKED_IN',
          expectedCheckOutDate,
          guests: [],
        });
        addToast(t('walkin_success'), 'success');
        navigate('/stays');
      } catch {
        setError(t('err_checkin_failed'));
      } finally {
        setLoading(false);
      }
    },
    [selectedRoomId, selectedGuest, expectedCheckOutDate, t, navigate, addToast],
  );

  const guestListLabel = useMemo(() => t('walkin_label_guest'), [t]);

  return (
    <main className="max-w-lg mx-auto p-6" aria-labelledby="walkin-title">
      <h1 id="walkin-title" className="text-2xl font-semibold text-on-surface mb-1">
        {t('walkin_title')}
      </h1>
      <p className="text-sm text-on-surface-variant mb-6">{t('walkin_subtitle')}</p>

      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {/* Room selection */}
        <div>
          <label htmlFor="walkin-room" className="block text-sm font-medium text-on-surface mb-1">
            {t('walkin_label_room')} <span aria-hidden="true">*</span>
          </label>
          {roomsLoading ? (
            <p className="text-sm text-on-surface-variant" role="status">Loading rooms…</p>
          ) : rooms.length === 0 ? (
            <p className="text-sm text-error" role="alert">{t('walkin_no_rooms')}</p>
          ) : (
            <select id="walkin-room" value={selectedRoomId} onChange={handleRoomChange} required
              className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary">
              <option value="">{t('walkin_placeholder_room')}</option>
              {rooms.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.roomNumber}{r.roomType?.name ? ` — ${r.roomType.name}` : ''}
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Guest search */}
        <div className="relative">
          <label htmlFor="walkin-guest" className="block text-sm font-medium text-on-surface mb-1">
            {t('walkin_label_guest')} <span aria-hidden="true">*</span>
          </label>
          <input id="walkin-guest" type="search" value={guestQuery}
            onChange={handleGuestQueryChange}
            placeholder={t('walkin_placeholder_guest')} autoComplete="off"
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary" />
          {guestResults.length > 0 && (
            <div role="listbox" aria-label={guestListLabel}
              className="absolute z-10 mt-1 w-full rounded-md border border-outline bg-surface shadow-md max-h-48 overflow-y-auto">
              {guestResults.map((g) => (
                <GuestOption key={g.id} guest={g}
                  selected={selectedGuest?.id === g.id}
                  onSelect={handleGuestSelect} />
              ))}
            </div>
          )}
        </div>

        {/* Expected check-out date */}
        <div>
          <label htmlFor="walkin-checkout" className="block text-sm font-medium text-on-surface mb-1">
            {t('walkin_label_checkout_date')} <span aria-hidden="true">*</span>
          </label>
          <input id="walkin-checkout" type="date" value={expectedCheckOutDate}
            min={todayIso} onChange={handleCheckoutChange} required
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary" />
        </div>

        {error && <p role="alert" className="text-sm text-error">{error}</p>}

        <div className="flex gap-3 pt-2">
          <button type="button" onClick={handleNavigateBack}
            className="flex-1 rounded-full border border-outline px-6 py-2 text-sm font-medium text-on-surface hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary">
            Cancel
          </button>
          <button type="submit" disabled={loading || rooms.length === 0}
            className="flex-1 rounded-full bg-primary px-6 py-2 text-sm font-medium text-on-primary hover:bg-primary/90 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-primary">
            {loading ? t('btn_processing') : t('walkin_btn_checkin')}
          </button>
        </div>
      </form>
    </main>
  );
}
