import { useState, useEffect, useCallback, useMemo, memo } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services/stayService';
import { guestService } from '../../services/guestService';
import type { AvailableRoom, AlloggiatiStato, AlloggiatiTipdoc, StayGuestRequest, TravellerType } from '../../types/stay.types';
import type { GuestResponseDTO } from '../../types/guest.types';
import { useToastStore } from '../../store/toastStore';
import { GuestFieldSection } from './StayGuestFieldSection';
import {
  emptyGuest,
  TYPES_WITHOUT_DOC,
  CODICE_ITALIA,
} from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

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
  const { t } = useTranslation(['stays', 'common']);
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

  // Alloggiati lookup tables
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);
  // Alloggiati guest data (one primary guest by default, additional guests can be added)
  const [guests, setGuests] = useState<IdentifiableGuest[]>([emptyGuest(true)]);

  useEffect(() => {
    setRoomsLoading(true);
    stayService
      .getAvailableRooms()
      .then(setRooms)
      .catch(() => setRooms([]))
      .finally(() => setRoomsLoading(false));
  }, []);

  useEffect(() => {
    stayService.getLookupStati().then(setStati).catch(() => { /* non-blocking */ });
    stayService.getLookupTipdoc().then(setTipdoc).catch(() => { /* non-blocking */ });
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
    // Pre-fill first guest section with the selected guest's name
    setGuests(prev => [{
      ...prev[0],
      firstName: guest.firstName,
      lastName: guest.lastName,
    }, ...prev.slice(1)]);
  }, []);

  const handleCheckoutChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setExpectedCheckOutDate(e.target.value);
  }, []);

  const handleNavigateBack = useCallback(() => navigate('/stays'), [navigate]);

  const handleGuestChange = useCallback((index: number, patch: Partial<IdentifiableGuest>) => {
    setGuests(prev => {
      const updated = [...prev];
      updated[index] = { ...updated[index], ...patch };
      if (patch.isPrimaryGuest === true) {
        return updated.map((g, i) => i === index ? g : { ...g, isPrimaryGuest: false });
      }
      return updated;
    });
  }, []);

  const addGuest = useCallback(() => setGuests(prev => [...prev, emptyGuest(false)]), []);
  const removeGuest = useCallback((index: number) => setGuests(prev => prev.filter((_, i) => i !== index)), []);

  const handleSubmit = useCallback(
    async (e: FormEvent) => {
      e.preventDefault();
      setError('');
      if (!selectedRoomId) { setError(t('walkin_err_room_required')); return; }
      if (!selectedGuest)   { setError(t('walkin_err_guest_required')); return; }
      if (!expectedCheckOutDate) { setError(t('walkin_err_checkout_required')); return; }

      // Per-guest Alloggiati validation
      for (const [idx, g] of guests.entries()) {
        const num = idx + 1;
        const gHasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
        const isItalianBorn = g._statoDiNascita === CODICE_ITALIA;
        const isItalianDocIssue = g._statoRilascioDoc === CODICE_ITALIA;

        if (!g._statoDiNascita) {
          setError(t('err_stato_nascita_required', { number: num }));
          return;
        }
        if (isItalianBorn && !g.placeOfBirth) {
          setError(t('err_comune_nascita_required', { number: num }));
          return;
        }
        if (gHasDoc) {
          if (!g._statoRilascioDoc) {
            setError(t('err_stato_rilascio_required', { number: num }));
            return;
          }
          if (isItalianDocIssue && !g.documentPlaceOfIssue) {
            setError(t('err_comune_rilascio_required', { number: num }));
            return;
          }
        }
      }

      setLoading(true);
      try {
        const apiGuests: StayGuestRequest[] = guests.map(g => {
          const withoutDoc = TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
          return {
            firstName: g.firstName,
            lastName: g.lastName,
            gender: g.gender,
            dateOfBirth: g.dateOfBirth,
            placeOfBirth: g.placeOfBirth,
            citizenship: g.citizenship,
            documentType: withoutDoc ? undefined : (g.documentType || undefined),
            documentNumber: withoutDoc ? undefined : (g.documentNumber || undefined),
            documentPlaceOfIssue: withoutDoc ? undefined : (g.documentPlaceOfIssue || undefined),
            isPrimaryGuest: g.isPrimaryGuest,
            travellerType: g.travellerType || undefined,
            travelPurpose: g.travelPurpose || undefined,
          };
        });

        await stayService.createStay({
          guestId: selectedGuest.id,
          roomId: selectedRoomId,
          status: 'CHECKED_IN',
          expectedCheckOutDate,
          guests: apiGuests,
        });
        addToast(t('walkin_success'), 'success');
        navigate('/stays');
      } catch {
        setError(t('err_checkin_failed'));
      } finally {
        setLoading(false);
      }
    },
    [selectedRoomId, selectedGuest, expectedCheckOutDate, guests, t, navigate, addToast],
  );

  const guestListLabel = useMemo(() => t('walkin_label_guest'), [t]);

  return (
    <main className="max-w-2xl mx-auto p-6" aria-labelledby="walkin-title">
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
            <p className="text-sm text-on-surface-variant" role="status">{t('walkin_loading_rooms')}</p>
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

        {/* Alloggiati guest data */}
        <div className="space-y-4">
          {guests.map((guest, index) => (
            <GuestFieldSection
              key={guest._id}
              guest={guest}
              index={index}
              canRemove={guests.length > 1}
              stati={stati}
              tipdoc={tipdoc}
              onRemove={removeGuest}
              onChange={handleGuestChange}
            />
          ))}
          <button
            type="button"
            onClick={addGuest}
            className="rounded-full border border-outline px-4 py-2 text-sm font-medium text-on-surface hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
          >
            {t('btn_add_guest')}
          </button>
        </div>

        {error && <p role="alert" className="text-sm text-error">{error}</p>}

        <div className="flex gap-3 pt-2">
          <button type="button" onClick={handleNavigateBack}
            className="flex-1 rounded-full border border-outline px-6 py-2 text-sm font-medium text-on-surface hover:bg-surface-variant focus:outline-none focus:ring-2 focus:ring-primary">
            {t('cancel')}
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
