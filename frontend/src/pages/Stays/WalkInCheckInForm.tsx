import { useState, useEffect, useCallback, useMemo, useRef, memo } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { stayService } from '../../services';
import { guestService } from '../../services';
import type {
  AvailableRoom, AlloggiatiStato, AlloggiatiTipdoc, CityTaxUnassessedReason, StayGuestRequest, TravellerType,
} from '../../types';
import type { GuestResponseDTO } from '../../types';
import { useToastStore } from '../../store';
import { Alert } from '../../components/Alert';
import { PageHeader } from '../../components/PageHeader';
import { M3Button } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3Select } from '../../components/m3';
import { CheckInChecklist } from './CheckInChecklist';
import { buildCheckInChecklist, buildWalkInItems } from './checkInReadiness';
import { GuestFieldSection } from './GuestFieldSection';
import {
  emptyGuest,
  profileDocumentPrefill,
  TYPES_WITHOUT_DOC,
  validateAlloggiatiGuests,
} from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';
import { getErrorMessage, todayIsoDate } from '../../utils';

const GUEST_SEARCH_DEBOUNCE_MS = 300;

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
        className="w-full text-left px-3 py-2 text-sm hover:bg-surface-variant focus:bg-surface-variant focus:outline-hidden focus:ring-2 focus:ring-primary focus:ring-inset">
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
  const [prefillFields, setPrefillFields] = useState<string[]>([]);
  // Bumped on every submit so a repeated identical error still remounts (re-announces) and re-scrolls.
  const [submitAttempt, setSubmitAttempt] = useState(0);
  const errorRef = useRef<HTMLDivElement>(null);
  const [roomsLoading, setRoomsLoading] = useState(true);

  // Alloggiati lookup tables
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);
  // Alloggiati guest data (one primary guest by default, additional guests can be added)
  const [guests, setGuests] = useState<IdentifiableGuest[]>([emptyGuest(true)]);
  const guestSearchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Pre-flight check (Parte 5.3): tells the operator before submitting that the
  // tourist tax won't actually be charged, instead of only discovering it later on
  // the monthly comune declaration. Never blocks the check-in itself.
  // `undefined` until the check resolves, so the checklist can tell "not checked yet" from "configured".
  const [cityTaxWarning, setCityTaxWarning] = useState<CityTaxUnassessedReason | null | undefined>(undefined);

  useEffect(() => {
    stayService.getCityTaxConfigurationStatus()
      .then((status) => setCityTaxWarning(status.configured ? null : (status.reason ?? null)))
      .catch(() => { /* non-blocking */ });
  }, []);

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

  const roomOptions = useMemo(() => rooms.map((r) => ({
    value: r.id,
    label: `${r.roomNumber}${r.roomType?.name ? ` — ${r.roomType.name}` : ''}`,
  })), [rooms]);

  const handleGuestQueryChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const query = e.target.value;
    setGuestQuery(query);
    setSelectedGuest(null);
    if (guestSearchDebounceRef.current !== null) clearTimeout(guestSearchDebounceRef.current);
    if (query.trim().length < 2) {
      setGuestResults([]);
      return;
    }
    guestSearchDebounceRef.current = setTimeout(async () => {
      try {
        const results = await guestService.searchGuests(query);
        setGuestResults(results);
      } catch {
        setGuestResults([]);
      }
    }, GUEST_SEARCH_DEBOUNCE_MS);
  }, []);

  const handleGuestSelect = useCallback((guest: GuestResponseDTO) => {
    setSelectedGuest(guest);
    setGuestQuery(`${guest.firstName} ${guest.lastName}`);
    setGuestResults([]);
    // Pre-fill the first guest section with the selected guest's name and primary document. The
    // previous guest's prefilled document is cleared first: it belongs to someone else.
    const { updates, filled } = profileDocumentPrefill(guest);
    setGuests(prev => [{
      ...prev[0],
      ...(prefillFields.length > 0 ? { documentType: '', documentNumber: '', documentExpiryDate: undefined } : {}),
      firstName: guest.firstName,
      lastName: guest.lastName,
      ...updates,
    }, ...prev.slice(1)]);
    setPrefillFields(filled);
  }, [prefillFields]);

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
      setSubmitAttempt((n) => n + 1);
      if (!selectedRoomId) { setError(t('walkin_err_room_required')); return; }
      if (!selectedGuest)   { setError(t('walkin_err_guest_required')); return; }
      if (!expectedCheckOutDate) { setError(t('walkin_err_checkout_required')); return; }

      const issue = validateAlloggiatiGuests(guests, t);
      if (issue) {
        setError(issue);
        return;
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

        const created = await stayService.createStay({
          guestId: selectedGuest.id,
          roomId: selectedRoomId,
          status: 'CHECKED_IN',
          expectedCheckOutDate,
          guests: apiGuests,
        });
        addToast(t('walkin_success'), 'success');
        // NOT_APPLICABLE is a deliberate hotel declaration, never a gap — only the
        // three configuration-gap reasons are worth surfacing here.
        if (created.cityTaxWarning && created.cityTaxWarning !== 'NOT_APPLICABLE') {
          addToast(t(`city_tax_post_checkin_warning_${created.cityTaxWarning.toLowerCase()}`), 'info');
        }
        navigate('/stays');
      } catch (err) {
        setError(getErrorMessage(err, t('err_checkin_failed')));
      } finally {
        setLoading(false);
      }
    },
    [selectedRoomId, selectedGuest, expectedCheckOutDate, guests, t, navigate, addToast],
  );

  const guestListLabel = useMemo(() => t('walkin_label_guest'), [t]);

  const roomLabel = roomOptions.find((o) => o.value === selectedRoomId)?.label ?? '';
  const checklist = useMemo(
    () => buildCheckInChecklist({
      guests,
      cityTaxWarning,
      checkOutDate: expectedCheckOutDate || undefined,
      extra: buildWalkInItems({
        roomLabel,
        guestName: selectedGuest ? `${selectedGuest.firstName} ${selectedGuest.lastName}`.trim() : '',
        checkOutDate: expectedCheckOutDate,
      }),
    }),
    [guests, cityTaxWarning, expectedCheckOutDate, roomLabel, selectedGuest],
  );

  // The error banner sits above a long form; bring it into view when submit is blocked.
  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView?.({ block: 'center' });
  }, [error, submitAttempt]);

  return (
    <div className="max-w-6xl mx-auto p-6">
      <PageHeader id="walkin-title" title={t('walkin_title')} subtitle={t('walkin_subtitle')} className="mb-6" />

      {cityTaxWarning && cityTaxWarning !== 'NOT_APPLICABLE' && (
        <Alert tone="warning" icon="info" title={t('city_tax_preflight_title')} className="mb-6">
          {t(`city_tax_preflight_reason_${cityTaxWarning.toLowerCase()}`)}
        </Alert>
      )}

      {prefillFields.length > 0 && (
        <Alert tone="warning" icon="auto_fix_high" className="mb-6">
          {t('prefill_banner_profile', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })}
        </Alert>
      )}

      {error && (
        <div ref={errorRef} key={submitAttempt} className="mb-6">
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_340px] gap-6 items-start">
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {/* Room selection */}
        <div>
          {roomsLoading ? (
            <>
              <p className="block text-sm font-medium text-on-surface mb-1">{t('walkin_label_room')}</p>
              <p className="text-sm text-on-surface-variant" role="status">{t('walkin_loading_rooms')}</p>
            </>
          ) : rooms.length === 0 ? (
            <>
              <p className="block text-sm font-medium text-on-surface mb-1">{t('walkin_label_room')}</p>
              <p className="text-sm text-error" role="alert">{t('walkin_no_rooms')}</p>
            </>
          ) : (
            <M3Select
              label={t('walkin_label_room')}
              required
              options={roomOptions}
              placeholder={t('walkin_placeholder_room')}
              value={selectedRoomId}
              onChange={handleRoomChange}
            />
          )}
        </div>

        {/* Guest search */}
        <div className="relative">
          <M3TextField
            label={t('walkin_label_guest')}
            required
            type="search"
            value={guestQuery}
            onChange={handleGuestQueryChange}
            placeholder={t('walkin_placeholder_guest')}
            autoComplete="off"
          />
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
        <M3TextField
          label={t('walkin_label_checkout_date')}
          required
          type="date"
          value={expectedCheckOutDate}
          min={todayIsoDate()}
          onChange={handleCheckoutChange}
        />

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
              showReadiness
            />
          ))}
          <M3Button type="button" variant="outlined" onClick={addGuest}>
            {t('btn_add_guest')}
          </M3Button>
        </div>

        <div className="flex gap-3 pt-2">
          <M3Button type="button" variant="outlined" onClick={handleNavigateBack} className="flex-1">
            {t('cancel')}
          </M3Button>
          <M3Button type="submit" loading={loading} disabled={rooms.length === 0} className="flex-1">
            {loading ? t('btn_processing') : t('walkin_btn_checkin')}
          </M3Button>
        </div>
      </form>
      <aside aria-label={t('checklist_title')} className="lg:sticky lg:top-6">
        <CheckInChecklist items={checklist} />
      </aside>
      </div>
    </div>
  );
}
