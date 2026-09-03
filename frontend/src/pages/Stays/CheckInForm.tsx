import { useState, useCallback, memo, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
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
  GuestDocumentType as DocumentType,
} from '../../types';
import { GuestFieldSection } from './GuestFieldSection';
import {
  emptyGuest,
  TYPES_WITHOUT_DOC,
  validateAlloggiatiGuests,
} from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

const mapDocType = (dt: DocumentType): string => {
  switch (dt) {
    case 'PASSPORT': return 'PASOR';
    case 'ID_CARD':  return 'CARTE';
    default:         return '';
  }
};

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

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [prefillFields, setPrefillFields] = useState<string[]>([]);
  const [prefillSource, setPrefillSource] = useState<'stay' | 'profile' | null>(null);
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);
  // Pre-flight check (Parte 5.3): tells the operator before submitting that the
  // tourist tax won't actually be charged, instead of only discovering it later on
  // the monthly comune declaration. Never blocks the check-in itself.
  const [cityTaxWarning, setCityTaxWarning] = useState<CityTaxUnassessedReason | null>(null);

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

  useEffect(() => {
    if (state || !reservationId) return; // location.state present, or no id to look up
    let cancelled = false;
    setContextLoading(true);
    reservationService.getReservationById(reservationId)
      .then((r) => {
        if (cancelled) return;
        const roomId = r.lineItems[0]?.roomId;
        if (roomId) {
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
        if (!updates.documentType   && doc?.documentType)   { updates.documentType   = mapDocType(doc.documentType); filled.push('documentType'); }
        if (!updates.documentNumber && doc?.documentNumber) { updates.documentNumber = doc.documentNumber;           filled.push('documentNumber'); }
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

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <M3Button variant="text" icon="arrow_back" onClick={handleBack}>{t('back')}</M3Button>
        <h1 className="text-2xl font-display font-bold text-on-surface">{t('checkin_title')}</h1>
      </div>

      {prefillFields.length > 0 && (
        <div className="bg-secondary-container text-on-secondary-container p-4 rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="auto_fix_high" className="mt-0.5 shrink-0" />
          <p className="font-body text-sm">
            {prefillSource === 'stay'
              ? t('prefill_banner_stay', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })
              : t('prefill_banner_profile', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })}
          </p>
        </div>
      )}

      {cityTaxWarning && cityTaxWarning !== 'NOT_APPLICABLE' && (
        <div
          role="status"
          className="bg-secondary-container text-on-secondary-container p-4 rounded-shape-sm flex items-start gap-3"
        >
          <MaterialIcon name="info" className="mt-0.5 shrink-0" />
          <div>
            <p className="font-body text-sm font-medium">{t('city_tax_preflight_title')}</p>
            <p className="font-body text-sm">{t(`city_tax_preflight_reason_${cityTaxWarning.toLowerCase()}`)}</p>
          </div>
        </div>
      )}

      {error && (
        <div className="bg-error-container text-on-error-container p-4 rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="error" className="mt-0.5 shrink-0" />
          <p className="font-body text-sm">{error}</p>
        </div>
      )}

      {contextLoading ? (
        <div className="flex items-center justify-center py-12">
          <MaterialIcon name="progress_activity" size={32} className="text-primary animate-spin" />
        </div>
      ) : (
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
      )}
    </div>
  );
});

CheckInForm.displayName = 'CheckInForm';
