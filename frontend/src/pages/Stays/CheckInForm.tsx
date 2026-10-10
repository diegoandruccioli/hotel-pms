import { useState, useCallback, memo, useEffect, useMemo, useRef } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { differenceInCalendarDays, parseISO } from 'date-fns';
import { useFormatters } from '../../hooks/useFormatters';
import { Alert } from '../../components/Alert';
import { M3LoadingState } from '../../components/m3';
import { PageHeader } from '../../components/PageHeader';
import { M3Button } from '../../components/m3';
import { stayService } from '../../services';
import { guestService } from '../../services';
import { reservationService } from '../../services';
import { useToastStore } from '../../store';
import type {
  AlloggiatiStato,
  AlloggiatiTipdoc,
  CityTaxUnassessedReason,
  StayGuestRequest,
  StayRequest,
  TravellerType,
} from '../../types';
import { CheckInChecklist } from './CheckInChecklist';
import { buildCheckInChecklist } from './checkInReadiness';
import { GuestFieldSection } from './GuestFieldSection';
import {
  emptyGuest,
  mapDocType,
  TYPES_WITHOUT_DOC,
  validateAlloggiatiGuests,
} from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

interface CheckInState {
  guestId: string;
  roomId: string;
  expectedGuests: number;
}

// ---------------------------------------------------------------------------
// CheckInForm
// ---------------------------------------------------------------------------
export const CheckInForm = memo(() => {
  const { t } = useTranslation(['stays', 'common']);
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const { reservationId } = useParams<{ reservationId: string }>();
  const location = useLocation();
  const state = location.state as CheckInState | null;
  const { formatDate } = useFormatters();
  const errorRef = useRef<HTMLDivElement>(null);
  // Bumped on every submit so a repeated identical error still remounts (re-announces) and re-scrolls.
  const [submitAttempt, setSubmitAttempt] = useState(0);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [prefillFields, setPrefillFields] = useState<string[]>([]);
  const [prefillSource, setPrefillSource] = useState<'stay' | 'profile' | null>(null);
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);
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

  // location.state (set by Reservations.tsx's handleCheckIn) is the normal path — but a
  // direct navigation, bookmark, or page refresh loses it entirely. Fall back to fetching
  // the reservation itself so this route is actually deep-linkable/refreshable, not just
  // reachable from one specific button.
  const [fallbackState, setFallbackState] = useState<CheckInState | null>(null);
  const [contextLoading, setContextLoading] = useState(!state && !!reservationId);
  const effectiveState = state ?? fallbackState;

  // The reservation is also what feeds the header subtitle and the document-expiry check, so it is
  // fetched even when location.state already carries the check-in context.
  const [reservationDates, setReservationDates] = useState<{ checkIn: string; checkOut: string; guests: number } | null>(null);

  useEffect(() => {
    if (!reservationId) return; // no id to look up
    let cancelled = false;
    if (!state) setContextLoading(true);
    reservationService.getReservationById(reservationId)
      .then((r) => {
        if (cancelled) return;
        if (typeof r.checkInDate === 'string' && typeof r.checkOutDate === 'string') {
          setReservationDates({ checkIn: r.checkInDate, checkOut: r.checkOutDate, guests: r.expectedGuests });
        }
        const roomId = r.lineItems[0]?.roomId;
        if (!state && roomId) {
          setFallbackState({ guestId: r.guestId, roomId, expectedGuests: r.expectedGuests });
        }
      })
      .catch(() => { /* leave fallbackState null — existing err_missing_context path still applies */ })
      .finally(() => { if (!cancelled) setContextLoading(false); });
    return () => { cancelled = true; };
  }, [state, reservationId]);

  const initialCount = state?.expectedGuests && state.expectedGuests > 0 ? state.expectedGuests : 1;
  const [guests, setGuests] = useState<IdentifiableGuest[]>(
    Array.from({ length: initialCount }, (_, i) => emptyGuest(i === 0))
  );

  // Resizes the guest list once the fallback fetch resolves — the useState initializer
  // above only runs on mount, before an async fallback could possibly have data yet.
  useEffect(() => {
    if (state || !fallbackState) return; // location.state already sized `guests` correctly
    const count = fallbackState.expectedGuests > 0 ? fallbackState.expectedGuests : 1;
    setGuests(prev => prev.length === count
      ? prev
      : Array.from({ length: count }, (_, i) => prev[i] ?? emptyGuest(i === 0)));
  }, [state, fallbackState]);

  useEffect(() => {
    stayService.getLookupStati().then(setStati).catch(() => { /* non-blocking */ });
    stayService.getLookupTipdoc().then(setTipdoc).catch(() => { /* non-blocking */ });
  }, []);

  const guestId = effectiveState?.guestId;
  useEffect(() => {
    if (!guestId) return;

    Promise.allSettled([
      stayService.getLastCompletedStayForGuest(guestId),
      guestService.getGuestById(guestId),
    ]).then(([stayResult, profileResult]) => {
      const updates: Partial<IdentifiableGuest> = {};
      const filled: string[] = [];

      const lastStay = stayResult.status === 'fulfilled' ? stayResult.value : null;
      const lastPrimary = lastStay?.guests?.find(g => g.isPrimaryGuest) ?? lastStay?.guests?.[0] ?? null;
      if (lastPrimary) {
        if (lastPrimary.firstName)    { updates.firstName    = lastPrimary.firstName;    filled.push('firstName'); }
        if (lastPrimary.lastName)     { updates.lastName     = lastPrimary.lastName;     filled.push('lastName'); }
        if (lastPrimary.gender)       { updates.gender       = lastPrimary.gender;       filled.push('gender'); }
        if (lastPrimary.dateOfBirth)  { updates.dateOfBirth  = lastPrimary.dateOfBirth;  filled.push('dateOfBirth'); }
        if (lastPrimary.citizenship)  { updates.citizenship  = lastPrimary.citizenship;  filled.push('citizenship'); }
        if (lastPrimary.placeOfBirth) { updates.placeOfBirth = lastPrimary.placeOfBirth; filled.push('placeOfBirth'); }
        if (lastPrimary.travellerType){ updates.travellerType= lastPrimary.travellerType; filled.push('travellerType'); }
      }

      const profile = profileResult.status === 'fulfilled' ? profileResult.value : null;
      if (profile) {
        const doc = profile.identityDocuments?.[0];
        if (!updates.firstName    && profile.firstName)    { updates.firstName    = profile.firstName;           filled.push('firstName'); }
        if (!updates.lastName     && profile.lastName)     { updates.lastName     = profile.lastName;            filled.push('lastName'); }
        // Only claim the field as pre-filled when the document maps to an Alloggiati code
        const tipdoc = doc?.documentType ? mapDocType(doc.documentType) : '';
        if (!updates.documentType   && tipdoc)              { updates.documentType   = tipdoc;                       filled.push('documentType'); }
        if (!updates.documentNumber && doc?.documentNumber) { updates.documentNumber = doc.documentNumber;           filled.push('documentNumber'); }
        if (!updates.documentExpiryDate && tipdoc && doc?.expiryDate) { updates.documentExpiryDate = doc.expiryDate; filled.push('documentExpiryDate'); }
      }

      if (Object.keys(updates).length === 0) return;
      setGuests(prev => [{ ...prev[0], ...updates }, ...prev.slice(1)]);
      setPrefillFields(filled);
      setPrefillSource(lastPrimary ? 'stay' : 'profile');
    });
  }, [guestId]);

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
  const handleBack = useCallback(() => navigate(-1), [navigate]);

  const handleSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitAttempt((n) => n + 1);

    if (!reservationId || !effectiveState?.roomId || !effectiveState?.guestId) {
      setError(t('err_missing_context'));
      return;
    }

    const issue = validateAlloggiatiGuests(guests, t);
    if (issue) {
      setError(issue);
      return;
    }

    try {
      setLoading(true);
      const apiGuests: StayGuestRequest[] = guests.map(g => {
        const withoutDoc = TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
        return {
          firstName: g.firstName,
          lastName: g.lastName,
          gender: g.gender,
          dateOfBirth: g.dateOfBirth,
          placeOfBirth: g.placeOfBirth,
          citizenship: g.citizenship,
          // Explicitly exclude doc fields for FAMILIARE/MEMBRO_GRUPPO per tracciato rules
          documentType: withoutDoc ? undefined : (g.documentType || undefined),
          documentNumber: withoutDoc ? undefined : (g.documentNumber || undefined),
          documentPlaceOfIssue: withoutDoc ? undefined : (g.documentPlaceOfIssue || undefined),
          isPrimaryGuest: g.isPrimaryGuest,
          travellerType: g.travellerType || undefined,
          travelPurpose: g.travelPurpose || undefined,
        };
      });

      const request: StayRequest = {
        reservationId,
        guestId: effectiveState.guestId,
        roomId: effectiveState.roomId,
        status: 'CHECKED_IN',
        guests: apiGuests,
      };

      const created = await stayService.createStay(request);
      // NOT_APPLICABLE is a deliberate hotel declaration, never a gap — only the
      // three configuration-gap reasons are worth surfacing here.
      if (created.cityTaxWarning && created.cityTaxWarning !== 'NOT_APPLICABLE') {
        addToast(t(`city_tax_post_checkin_warning_${created.cityTaxWarning.toLowerCase()}`), 'info');
      }
      navigate('/stays', { replace: true });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      setError(e.response?.data?.detail || e.message || t('err_checkin_failed'));
    } finally {
      setLoading(false);
    }
  }, [reservationId, effectiveState, guests, navigate, t, addToast]);

  const checklist = useMemo(
    () => buildCheckInChecklist({ guests, cityTaxWarning, checkOutDate: reservationDates?.checkOut }),
    [guests, cityTaxWarning, reservationDates],
  );

  const subtitle = reservationDates
    ? t('checkin_subtitle', {
      checkIn: formatDate(reservationDates.checkIn),
      checkOut: formatDate(reservationDates.checkOut),
      nights: t('checkin_nights', {
        count: differenceInCalendarDays(parseISO(reservationDates.checkOut), parseISO(reservationDates.checkIn)),
      }),
      guests: t('checkin_guests', { count: reservationDates.guests }),
    })
    : undefined;

  // The error banner sits above a long form; bring it into view when submit is blocked.
  useEffect(() => {
    if (error) errorRef.current?.scrollIntoView?.({ block: 'center' });
  }, [error, submitAttempt]);

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <PageHeader title={t('checkin_title')} subtitle={subtitle} onBack={handleBack} />

      {prefillFields.length > 0 && (
        <Alert tone="warning" icon="auto_fix_high">
          {prefillSource === 'stay'
            ? t('prefill_banner_stay', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })
            : t('prefill_banner_profile', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })}
        </Alert>
      )}

      {cityTaxWarning && cityTaxWarning !== 'NOT_APPLICABLE' && (
        <Alert tone="warning" icon="info" title={t('city_tax_preflight_title')}>
          {t(`city_tax_preflight_reason_${cityTaxWarning.toLowerCase()}`)}
        </Alert>
      )}

      {error && (
        <div ref={errorRef} key={submitAttempt}>
          <Alert tone="error">{error}</Alert>
        </div>
      )}

      {contextLoading ? (
        <M3LoadingState label={t('common:loading')} plain className="h-auto py-12" />
      ) : (
      <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_340px] gap-6 items-start">
      <form onSubmit={handleSubmit} noValidate className="space-y-6">
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

        <div className="flex gap-4 items-center justify-between border-t border-outline-variant pt-6">
          <M3Button variant="outlined" icon="person_add" onClick={addGuest} type="button">
            {t('btn_add_guest')}
          </M3Button>
          <M3Button variant="filled" icon="how_to_reg" type="submit" disabled={loading}>
            {loading ? t('btn_processing') : t('btn_complete_checkin')}
          </M3Button>
        </div>
      </form>
      <aside aria-label={t('checklist_title')} className="lg:sticky lg:top-6">
        <CheckInChecklist items={checklist} />
      </aside>
      </div>
      )}
    </div>
  );
});

CheckInForm.displayName = 'CheckInForm';
