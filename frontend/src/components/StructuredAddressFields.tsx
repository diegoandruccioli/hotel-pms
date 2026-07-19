import { useState, useCallback, useRef, useEffect, memo } from 'react';
import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { stayService } from '../services/stayService';
import type { AlloggiatiComune } from '../types/stay.types';

const AUTOCOMPLETE_DEBOUNCE_MS = 300;
const AUTOCOMPLETE_MIN_CHARS = 2;

interface ComuneOptionProps {
  option: AlloggiatiComune;
  selected: boolean;
  onSelect: (option: AlloggiatiComune) => void;
}

const ComuneOption = memo(({ option, selected, onSelect }: ComuneOptionProps) => {
  const handleMouseDown = useCallback(() => onSelect(option), [option, onSelect]);
  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ') onSelect(option);
  }, [option, onSelect]);

  return (
    <div
      role="option"
      aria-selected={selected}
      tabIndex={0}
      onMouseDown={handleMouseDown}
      onKeyDown={handleKeyDown}
      className="px-4 py-2 text-sm cursor-pointer hover:bg-surface-variant text-on-surface"
    >
      {option.descrizione} <span className="text-on-surface-variant">({option.provincia})</span>
    </div>
  );
});
ComuneOption.displayName = 'ComuneOption';

export interface StructuredAddressFieldsProps {
  idPrefix: string;
  cap: string;
  comune: string;
  provincia: string;
  onCapChange: (value: string) => void;
  onComuneChange: (value: string) => void;
  onProvinciaChange: (value: string) => void;
}

/**
 * CAP + Comune (server-side autocomplete) + Provincia inputs for the Italian
 * structured address required by the FatturaPA XML `Sede` (P0-1). Selecting a
 * Comune from the dropdown auto-fills Provincia; all three fields stay plain
 * editable text so the backend (source of truth) is what ultimately validates
 * the Comune/Provincia pair — this is a typing aid, not a hard picker.
 */
export const StructuredAddressFields = memo(({
  idPrefix, cap, comune, provincia, onCapChange, onComuneChange, onProvinciaChange,
}: StructuredAddressFieldsProps) => {
  const { t } = useTranslation('common');
  const [options, setOptions] = useState<AlloggiatiComune[]>([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleComuneInput = useCallback((e: ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    onComuneChange(value);
    if (debounceRef.current !== null) clearTimeout(debounceRef.current);
    if (value.trim().length < AUTOCOMPLETE_MIN_CHARS) {
      setOpen(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const comuni = await stayService.searchLookupComuni(value);
        setOptions(comuni);
        setOpen(comuni.length > 0);
      } catch {
        setOptions([]);
        setOpen(false);
      } finally {
        setLoading(false);
      }
    }, AUTOCOMPLETE_DEBOUNCE_MS);
  }, [onComuneChange]);

  const handleSelect = useCallback((option: AlloggiatiComune) => {
    onComuneChange(option.descrizione);
    onProvinciaChange(option.provincia);
    setOpen(false);
  }, [onComuneChange, onProvinciaChange]);

  const handleCapChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => onCapChange(e.target.value),
    [onCapChange],
  );
  const handleProvinciaChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => onProvinciaChange(e.target.value.toUpperCase()),
    [onProvinciaChange],
  );

  const comuneId = `${idPrefix}-comune`;
  const capId = `${idPrefix}-cap`;
  const provinciaId = `${idPrefix}-provincia`;

  return (
    <div className="space-y-4">
      <div ref={containerRef} className="relative">
        <label htmlFor={comuneId} className="block text-sm font-medium text-on-surface mb-1">
          {t('label_comune')}
        </label>
        <div className="relative flex items-center rounded-md border border-outline bg-surface
          focus-within:ring-2 focus-within:ring-primary">
          <input
            id={comuneId}
            role="combobox"
            type="text"
            autoComplete="off"
            value={comune}
            onChange={handleComuneInput}
            aria-expanded={open}
            aria-haspopup="listbox"
            aria-controls={`${comuneId}-listbox`}
            aria-autocomplete="list"
            placeholder={t('placeholder_comune')}
            className="w-full bg-transparent px-3 py-2 text-sm text-on-surface focus:outline-none"
          />
          {loading && (
            <span className="material-symbols-outlined absolute right-3 animate-spin text-on-surface-variant text-base" aria-hidden="true">
              refresh
            </span>
          )}
        </div>
        {open && options.length > 0 && (
          <div
            id={`${comuneId}-listbox`}
            role="listbox"
            className="absolute z-50 w-full mt-1 bg-surface border border-outline rounded-md shadow-lg max-h-48 overflow-y-auto"
          >
            {options.map((option) => (
              <ComuneOption
                key={option.codice}
                option={option}
                selected={option.descrizione === comune}
                onSelect={handleSelect}
              />
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor={capId} className="block text-sm font-medium text-on-surface mb-1">
            {t('label_cap')}
          </label>
          <input
            id={capId}
            type="text"
            inputMode="numeric"
            maxLength={5}
            value={cap}
            onChange={handleCapChange}
            placeholder={t('placeholder_cap')}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface
              focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div>
          <label htmlFor={provinciaId} className="block text-sm font-medium text-on-surface mb-1">
            {t('label_provincia')}
          </label>
          <input
            id={provinciaId}
            type="text"
            maxLength={2}
            value={provincia}
            onChange={handleProvinciaChange}
            placeholder={t('placeholder_provincia')}
            className="w-full rounded-md border border-outline bg-surface px-3 py-2 text-sm text-on-surface
              focus:outline-none focus:ring-2 focus:ring-primary uppercase"
          />
        </div>
      </div>
    </div>
  );
});
StructuredAddressFields.displayName = 'StructuredAddressFields';
