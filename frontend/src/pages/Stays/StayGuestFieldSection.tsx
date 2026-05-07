import { useState, useCallback, memo, useRef, useMemo, useEffect } from 'react';
import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button } from '../../components/m3/M3Button';
import { M3Card } from '../../components/m3/M3Card';
import { M3TextField } from '../../components/m3/M3TextField';
import { stayService } from '../../services/stayService';
import type { AlloggiatiStato, AlloggiatiTipdoc, TravellerType } from '../../types/stay.types';
import {
  TYPES_WITHOUT_DOC,
  CODICE_ITALIA,
} from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

const AUTOCOMPLETE_DEBOUNCE_MS = 300;
const AUTOCOMPLETE_MIN_CHARS = 2;
const ICON_SIZE_20 = { fontSize: 20 };

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
export interface GuestFieldSectionProps {
  guest: IdentifiableGuest;
  index: number;
  canRemove: boolean;
  stati: AlloggiatiStato[];
  tipdoc: AlloggiatiTipdoc[];
  onRemove: (idx: number) => void;
  onChange: (idx: number, patch: Partial<IdentifiableGuest>) => void;
}

export const GuestFieldSection = memo(({
  guest, index, canRemove, stati, tipdoc, onRemove, onChange,
}: GuestFieldSectionProps) => {
  const { t } = useTranslation('stays');
  const hasDoc = !TYPES_WITHOUT_DOC.includes(guest.travellerType as TravellerType);
  const isItalianBorn = guest._statoDiNascita === CODICE_ITALIA;
  const isItalianDocIssue = guest._statoRilascioDoc === CODICE_ITALIA;

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

  const handleCitizenshipSelect = useCallback(
    (codice: string) => onChange(index, { citizenship: codice }),
    [index, onChange],
  );

  const handleStatoDiNascitaSelect = useCallback((codice: string) => {
    if (codice !== CODICE_ITALIA) {
      onChange(index, { _statoDiNascita: codice, placeOfBirth: codice });
    } else {
      onChange(index, { _statoDiNascita: codice, placeOfBirth: '' });
    }
  }, [index, onChange]);

  const handleComuneDiNascitaSelect = useCallback(
    (codice: string) => onChange(index, { placeOfBirth: codice }),
    [index, onChange],
  );

  const handleStatoRilascioSelect = useCallback((codice: string) => {
    if (codice !== CODICE_ITALIA) {
      onChange(index, { _statoRilascioDoc: codice, documentPlaceOfIssue: codice });
    } else {
      onChange(index, { _statoRilascioDoc: codice, documentPlaceOfIssue: '' });
    }
  }, [index, onChange]);

  const handleComuneRilascioSelect = useCallback(
    (codice: string) => onChange(index, { documentPlaceOfIssue: codice }),
    [index, onChange],
  );

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
