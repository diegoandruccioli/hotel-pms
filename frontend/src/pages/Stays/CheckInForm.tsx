import { useState, useCallback, memo, useEffect } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { stayService } from '../../services/stayService';
import { guestService } from '../../services/guestService';
import type {
  AlloggiatiStato,
  AlloggiatiTipdoc,
  StayGuestRequest,
  StayRequest,
  TravellerType,
} from '../../types/stay.types';
import type { DocumentType } from '../../types/guest.types';
import { GuestFieldSection } from './StayGuestFieldSection';
import {
  emptyGuest,
  TYPES_WITHOUT_DOC,
  CODICE_ITALIA,
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
  const { reservationId } = useParams<{ reservationId: string }>();
  const location = useLocation();
  const state = location.state as CheckInState | null;

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [prefillFields, setPrefillFields] = useState<string[]>([]);
  const [prefillSource, setPrefillSource] = useState<'stay' | 'profile' | null>(null);
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);

  const initialCount = state?.expectedGuests && state.expectedGuests > 0 ? state.expectedGuests : 1;
  const [guests, setGuests] = useState<IdentifiableGuest[]>(
    Array.from({ length: initialCount }, (_, i) => emptyGuest(i === 0))
  );

  useEffect(() => {
    stayService.getLookupStati().then(setStati).catch(() => { /* non-blocking */ });
    stayService.getLookupTipdoc().then(setTipdoc).catch(() => { /* non-blocking */ });
  }, []);

  const guestId = state?.guestId;
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

    if (!reservationId || !state?.roomId || !state?.guestId) {
      setError(t('err_missing_context'));
      return;
    }
    if (!guests.some(g => g.isPrimaryGuest)) {
      setError(t('err_primary_guest_required'));
      return;
    }

    // Per-guest domain validation
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
        guestId: state.guestId,
        roomId: state.roomId,
        status: 'CHECKED_IN',
        guests: apiGuests,
      };

      await stayService.createStay(request);
      navigate('/stays', { replace: true });
    } catch (err: unknown) {
      const e = err as { response?: { data?: { detail?: string } }; message?: string };
      setError(e.response?.data?.detail || e.message || t('err_checkin_failed'));
    } finally {
      setLoading(false);
    }
  }, [reservationId, state, guests, navigate, t]);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <M3Button variant="text" icon="arrow_back" onClick={handleBack}>{t('back')}</M3Button>
        <h1 className="text-2xl font-display font-bold text-on-surface">{t('checkin_title')}</h1>
      </div>

      {prefillFields.length > 0 && (
        <div className="bg-secondary-container text-on-secondary-container p-4 rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="auto_fix_high" className="mt-0.5 flex-shrink-0" />
          <p className="font-body text-sm">
            {prefillSource === 'stay'
              ? t('prefill_banner_stay', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })
              : t('prefill_banner_profile', { fields: prefillFields.map(f => t(`prefill_field_${f}`)).join(', ') })}
          </p>
        </div>
      )}

      {error && (
        <div className="bg-error-container text-on-error-container p-4 rounded-shape-sm flex items-start gap-3">
          <MaterialIcon name="error" className="mt-0.5 flex-shrink-0" />
          <p className="font-body text-sm">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
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
    </div>
  );
});

CheckInForm.displayName = 'CheckInForm';
