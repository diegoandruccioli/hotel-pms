import { useState, useCallback, memo, useEffect, useRef, useMemo } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3TextField } from '../../components/m3/M3TextField';
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

const mapDocType = (dt: DocumentType): string => {
  switch (dt) {
    case 'PASSPORT': return 'PASOR';
    case 'ID_CARD':  return 'CARTE';
    default:         return '';
  }
};

const ICON_SIZE_20 = { fontSize: 20 };
const AUTOCOMPLETE_DEBOUNCE_MS = 300;
const AUTOCOMPLETE_MIN_CHARS = 2;
const TYPES_WITHOUT_DOC: TravellerType[] = ['FAMILIARE', 'MEMBRO_GRUPPO'];
const CODICE_ITALIA = '100000100';

interface CheckInState {
  guestId: string;
  roomId: string;
  expectedGuests: number;
}

interface IdentifiableGuest extends StayGuestRequest {
  _id: string;
  _statoDiNascita: string;
  _statoRilascioDoc: string;
}

const emptyGuest = (isPrimary: boolean): IdentifiableGuest => ({
  _id: Math.random().toString(36).substring(2, 11),
  firstName: '',
  lastName: '',
  gender: '',
  dateOfBirth: '',
  placeOfBirth: '',
  citizenship: '',
  documentType: '',
  documentNumber: '',
  documentPlaceOfIssue: '',
  isPrimaryGuest: isPrimary,
  travellerType: isPrimary ? 'OSPITE_SINGOLO' : undefined,
  travelPurpose: '',
  _statoDiNascita: '',
  _statoRilascioDoc: '',
});

// ---------------------------------------------------------------------------
// LookupAutocomplete — server-side typeahead for Alloggiati Web lookup tables
// ---------------------------------------------------------------------------
interface LookupOption { codice: string; label: string; }

interface LookupOptionItemProps {
  option: LookupOption;
  selected: boolean;
  onSelect: (opt: LookupOption) => void;
}

const LookupOptionItem = memo(({ option, selected, onSelect }: LookupOptionItemProps) => {
  const handleMouseDown = useCallback(() => onSelect(option), [option, onSelect]);
  return (
    <div
      role="option"
      aria-selected={selected}
      tabIndex={0}
      onMouseDown={handleMouseDown}
      className="px-4 py-2 text-sm cursor-pointer hover:bg-surface-variant text-on-surface"
    >
      <span className="font-mono text-xs text-on-surface-variant mr-2">{option.codice}</span>
      {option.label}
    </div>
  );
});
LookupOptionItem.displayName = 'LookupOptionItem';

interface LookupAutocompleteProps {
  id: string;
  label: string;
  value: string;
  options: LookupOption[];
  loading: boolean;
  onSearchChange: (term: string) => void;
  onSelect: (codice: string) => void;
  required?: boolean;
  disabled?: boolean;
}

const LookupAutocomplete = memo(({
  id, label, value, options, loading, onSearchChange, onSelect, required, disabled,
}: LookupAutocompleteProps) => {
  const [editValue, setEditValue] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const displayLabel = useMemo(() => {
    if (!value) return '';
    const matched = options.find(o => o.codice === value);
    return matched ? `${matched.codice} — ${matched.label}` : value;
  }, [value, options]);

  const inputDisplayValue = isEditing ? editValue : displayLabel;

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
        setIsEditing(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleInput = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    setEditValue(v);
    setIsEditing(true);
    if (v.length >= AUTOCOMPLETE_MIN_CHARS) {
      onSearchChange(v);
      setOpen(true);
    } else {
      setOpen(false);
    }
  }, [onSearchChange]);

  const handleSelect = useCallback((opt: LookupOption) => {
    setIsEditing(false);
    setEditValue('');
    onSelect(opt.codice);
    setOpen(false);
  }, [onSelect]);

  const handleFocus = useCallback(() => {
    if (value) {
      setEditValue('');
      setIsEditing(true);
    }
  }, [value]);

  return (
    <div ref={containerRef} className="relative">
      <div className="relative flex items-center rounded-shape-xs border transition-colors border-outline hover:border-on-surface focus-within:border-primary">
        <input
          id={id}
          role="combobox"
          type="text"
          autoComplete="off"
          value={inputDisplayValue}
          onChange={handleInput}
          onFocus={handleFocus}
          required={required}
          disabled={disabled}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-controls={`${id}-listbox`}
          aria-autocomplete="list"
          className="peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface focus:outline-none"
        />
        <label
          htmlFor={id}
          className="absolute transition-all duration-150 pointer-events-none font-body left-4 top-1 text-xs text-on-surface-variant"
        >
          {label}{required === true && ' *'}
        </label>
        {loading && (
          <span className="material-symbols-outlined absolute right-3 animate-spin text-on-surface-variant text-base">
            refresh
          </span>
        )}
      </div>
      {open && options.length > 0 && (
        <div
          id={`${id}-listbox`}
          role="listbox"
          className="absolute z-50 w-full mt-1 bg-surface border border-outline rounded-shape-xs shadow-lg max-h-48 overflow-y-auto"
        >
          {options.map(opt => (
            <LookupOptionItem
              key={opt.codice}
              option={opt}
              selected={opt.codice === value}
              onSelect={handleSelect}
            />
          ))}
        </div>
      )}
    </div>
  );
});
LookupAutocomplete.displayName = 'LookupAutocomplete';

// ---------------------------------------------------------------------------
// StatoSelect — autocomplete backed by pre-loaded stati list
// ---------------------------------------------------------------------------
interface StatoSelectProps {
  id: string;
  label: string;
  value: string;
  stati: AlloggiatiStato[];
  onChange: (codice: string) => void;
  required?: boolean;
}

const StatoSelect = memo(({ id, label, value, stati, onChange, required }: StatoSelectProps) => {
  const [query, setQuery] = useState('');
  const options = useMemo<LookupOption[]>(() => {
    const q = query.toLowerCase();
    const filtered = query.length >= AUTOCOMPLETE_MIN_CHARS
      ? stati
          .filter(s => s.descrizione.toLowerCase().includes(q))
          .sort((a, b) => {
            const aPrefix = a.descrizione.toLowerCase().startsWith(q) ? 0 : 1;
            const bPrefix = b.descrizione.toLowerCase().startsWith(q) ? 0 : 1;
            return aPrefix - bPrefix || a.descrizione.localeCompare(b.descrizione);
          })
          .slice(0, 20)
      : stati.slice(0, 20);
    return filtered.map(s => ({ codice: s.codice, label: s.descrizione }));
  }, [stati, query]);

  const handleSearch = useCallback((term: string) => setQuery(term), []);
  const handleSelect = useCallback((codice: string) => { setQuery(''); onChange(codice); }, [onChange]);

  return (
    <LookupAutocomplete
      id={id}
      label={label}
      value={value}
      options={options}
      loading={false}
      onSearchChange={handleSearch}
      onSelect={handleSelect}
      required={required}
    />
  );
});
StatoSelect.displayName = 'StatoSelect';

// ---------------------------------------------------------------------------
// ComuneAutocomplete — server-side autocomplete for comuni
// ---------------------------------------------------------------------------
interface ComuneAutocompleteProps {
  id: string;
  label: string;
  value: string;
  onSelect: (codice: string) => void;
  required?: boolean;
}

const ComuneAutocomplete = memo(({ id, label, value, onSelect, required }: ComuneAutocompleteProps) => {
  const [options, setOptions] = useState<LookupOption[]>([]);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleSearch = useCallback((term: string) => {
    if (debounceRef.current !== null) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const comuni = await stayService.searchLookupComuni(term);
        const sorted = [...comuni].sort((a, b) => {
          const q = term.toLowerCase();
          const aPrefix = a.descrizione.toLowerCase().startsWith(q) ? 0 : 1;
          const bPrefix = b.descrizione.toLowerCase().startsWith(q) ? 0 : 1;
          return aPrefix - bPrefix || a.descrizione.localeCompare(b.descrizione);
        });
        setOptions(sorted.map(c => ({ codice: c.codice, label: `${c.descrizione} (${c.provincia})` })));
      } catch {
        setOptions([]);
      } finally {
        setLoading(false);
      }
    }, AUTOCOMPLETE_DEBOUNCE_MS);
  }, []);

  return (
    <LookupAutocomplete
      id={id}
      label={label}
      value={value}
      options={options}
      loading={loading}
      onSearchChange={handleSearch}
      onSelect={onSelect}
      required={required}
    />
  );
});
ComuneAutocomplete.displayName = 'ComuneAutocomplete';

// ---------------------------------------------------------------------------
// GuestFieldSection
// ---------------------------------------------------------------------------
interface GuestFieldSectionProps {
  guest: IdentifiableGuest;
  index: number;
  canRemove: boolean;
  stati: AlloggiatiStato[];
  tipdoc: AlloggiatiTipdoc[];
  onRemove: (idx: number) => void;
  onChange: (idx: number, patch: Partial<IdentifiableGuest>) => void;
}

const GuestFieldSection = memo(({
  guest, index, canRemove, stati, tipdoc, onRemove, onChange,
}: GuestFieldSectionProps) => {
  const { t } = useTranslation('stays');
  const hasDoc = !TYPES_WITHOUT_DOC.includes(guest.travellerType as TravellerType);
  const isItalianBorn = guest._statoDiNascita === '100000100';
  const isItalianDocIssue = guest._statoRilascioDoc === '100000100';

  const handleSimpleChange = useCallback((e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    onChange(index, { [e.target.name]: e.target.value } as Partial<IdentifiableGuest>);
  }, [index, onChange]);

  const handleTravellerTypeChange = useCallback((e: ChangeEvent<HTMLSelectElement>) => {
    const type = e.target.value as TravellerType;
    if (TYPES_WITHOUT_DOC.includes(type)) {
      onChange(index, { travellerType: type, documentType: '', documentNumber: '', documentPlaceOfIssue: '' });
    } else {
      onChange(index, { travellerType: type });
    }
  }, [index, onChange]);

  const handleRemove = useCallback(() => onRemove(index), [index, onRemove]);

  const handleCitizenshipSelect = useCallback((codice: string) => onChange(index, { citizenship: codice }), [index, onChange]);

  const handleStatoDiNascitaSelect = useCallback((codice: string) => {
    if (codice !== CODICE_ITALIA) {
      // foreign: placeOfBirth = stato code; clear any previous comune selection
      onChange(index, { _statoDiNascita: codice, placeOfBirth: codice });
    } else {
      // Italian: placeOfBirth = comune code (to be filled by ComuneAutocomplete)
      onChange(index, { _statoDiNascita: codice, placeOfBirth: '' });
    }
  }, [index, onChange]);

  const handleComuneDiNascitaSelect = useCallback((codice: string) => onChange(index, { placeOfBirth: codice }), [index, onChange]);

  const handleStatoRilascioSelect = useCallback((codice: string) => {
    if (codice !== CODICE_ITALIA) {
      // foreign: documentPlaceOfIssue = stato code; clear any previous comune
      onChange(index, { _statoRilascioDoc: codice, documentPlaceOfIssue: codice });
    } else {
      // Italian: documentPlaceOfIssue = comune code (to be filled by ComuneAutocomplete)
      onChange(index, { _statoRilascioDoc: codice, documentPlaceOfIssue: '' });
    }
  }, [index, onChange]);

  const handleComuneRilascioSelect = useCallback((codice: string) => onChange(index, { documentPlaceOfIssue: codice }), [index, onChange]);

  const handlePrimaryChange = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    onChange(index, { isPrimaryGuest: e.target.checked });
  }, [index, onChange]);

  return (
    <M3Card className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-display font-medium text-on-surface flex items-center gap-2">
          <MaterialIcon name="person" className="text-primary" />
          {t('guest_number', { number: index + 1 })}
          {guest.isPrimaryGuest && (
            <span className="text-xs bg-primary text-on-primary px-2 py-0.5 rounded-full">
              {t('guest_badge_primary')}
            </span>
          )}
        </h2>
        {canRemove && (
          <M3Button variant="text" icon="close" onClick={handleRemove} type="button">
            {t('btn_remove')}
          </M3Button>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <M3TextField label={t('label_first_name')} name="firstName" value={guest.firstName} onChange={handleSimpleChange} required />
        <M3TextField label={t('label_last_name')} name="lastName" value={guest.lastName} onChange={handleSimpleChange} required />

        <div className="relative">
          <div className="relative flex items-center rounded-shape-xs border transition-colors border-outline hover:border-on-surface">
            <select
              id={`gender-${index}`}
              name="gender"
              value={guest.gender}
              onChange={handleSimpleChange}
              required
              className="peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface focus:outline-none appearance-none"
            >
              <option value="" disabled hidden />
              <option value="1">{t('gender_male')}</option>
              <option value="2">{t('gender_female')}</option>
            </select>
            <label htmlFor={`gender-${index}`} className="absolute pointer-events-none font-body left-4 top-1 text-xs text-on-surface-variant">
              {t('label_gender')} *
            </label>
            <span className="material-symbols-outlined absolute right-3 pointer-events-none text-on-surface-variant z-10" style={ICON_SIZE_20}>arrow_drop_down</span>
          </div>
        </div>

        <M3TextField label={t('label_date_of_birth')} name="dateOfBirth" type="date" value={guest.dateOfBirth} onChange={handleSimpleChange} required />

        <StatoSelect
          id={`citizenship-${index}`}
          label={t('label_citizenship')}
          value={guest.citizenship}
          stati={stati}
          onChange={handleCitizenshipSelect}
          required
        />

        <div className="relative">
          <div className="relative flex items-center rounded-shape-xs border transition-colors border-outline hover:border-on-surface">
            <select
              id={`traveller-type-${index}`}
              name="travellerType"
              value={guest.travellerType ?? ''}
              onChange={handleTravellerTypeChange}
              required
              className="peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface focus:outline-none appearance-none"
            >
              <option value="" disabled hidden />
              <option value="OSPITE_SINGOLO">{t('guest_type_single')}</option>
              <option value="CAPOFAMIGLIA">{t('guest_type_family_head')}</option>
              <option value="CAPOGRUPPO">{t('guest_type_group_head')}</option>
              <option value="FAMILIARE">{t('guest_type_family_member')}</option>
              <option value="MEMBRO_GRUPPO">{t('guest_type_group_member')}</option>
            </select>
            <label htmlFor={`traveller-type-${index}`} className="absolute pointer-events-none font-body left-4 top-1 text-xs text-on-surface-variant">
              {t('label_guest_type')} *
            </label>
            <span className="material-symbols-outlined absolute right-3 pointer-events-none text-on-surface-variant z-10" style={ICON_SIZE_20}>arrow_drop_down</span>
          </div>
        </div>

        <StatoSelect
          id={`stato-nascita-${index}`}
          label={t('label_stato_nascita')}
          value={guest._statoDiNascita}
          stati={stati}
          onChange={handleStatoDiNascitaSelect}
          required
        />
        {isItalianBorn && (
          <ComuneAutocomplete
            id={`comune-nascita-${index}`}
            label={t('label_comune_nascita')}
            value={guest.placeOfBirth}
            onSelect={handleComuneDiNascitaSelect}
            required
          />
        )}

        {hasDoc && (
          <>
            <div className="relative">
              <div className="relative flex items-center rounded-shape-xs border transition-colors border-outline hover:border-on-surface">
                <select
                  id={`doc-type-${index}`}
                  name="documentType"
                  value={guest.documentType ?? ''}
                  onChange={handleSimpleChange}
                  required
                  className="peer w-full bg-transparent px-4 pt-5 pb-1.5 text-sm font-body text-on-surface focus:outline-none appearance-none"
                >
                  <option value="" disabled hidden />
                  {tipdoc.map(d => (
                    <option key={d.codice} value={d.codice}>{d.codice} — {d.descrizione}</option>
                  ))}
                </select>
                <label htmlFor={`doc-type-${index}`} className="absolute pointer-events-none font-body left-4 top-1 text-xs text-on-surface-variant">
                  {t('label_doc_type')} *
                </label>
                <span className="material-symbols-outlined absolute right-3 pointer-events-none text-on-surface-variant z-10" style={ICON_SIZE_20}>arrow_drop_down</span>
              </div>
            </div>

            <M3TextField label={t('label_doc_number')} name="documentNumber" value={guest.documentNumber ?? ''} onChange={handleSimpleChange} required />

            <StatoSelect
              id={`stato-rilascio-${index}`}
              label={t('label_stato_rilascio_doc')}
              value={guest._statoRilascioDoc}
              stati={stati}
              onChange={handleStatoRilascioSelect}
              required
            />
            {isItalianDocIssue && (
              <ComuneAutocomplete
                id={`comune-rilascio-${index}`}
                label={t('label_comune_rilascio_doc')}
                value={guest.documentPlaceOfIssue ?? ''}
                onSelect={handleComuneRilascioSelect}
                required
              />
            )}
          </>
        )}

        <M3TextField label={t('label_stay_reason')} name="travelPurpose" value={guest.travelPurpose ?? ''} onChange={handleSimpleChange} />

        <div className="md:col-span-2 flex items-center gap-2 mt-2">
          <input
            id={`primary-${index}`}
            type="checkbox"
            name="isPrimaryGuest"
            checked={guest.isPrimaryGuest}
            onChange={handlePrimaryChange}
            className="w-5 h-5 text-primary rounded focus:ring-primary"
          />
          <label htmlFor={`primary-${index}`} className="text-sm font-body text-on-surface cursor-pointer">
            {t('label_primary_guest')}
          </label>
        </div>
      </div>
    </M3Card>
  );
});
GuestFieldSection.displayName = 'GuestFieldSection';

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
