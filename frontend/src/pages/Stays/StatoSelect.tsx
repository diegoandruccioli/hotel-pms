import { useState, useCallback, memo, useMemo } from 'react';
import type { AlloggiatiStato } from '../../types';
import type { LookupOption } from './LookupOptionItem';
import { AUTOCOMPLETE_MIN_CHARS } from './LookupOptionItem';
import { LookupAutocomplete } from './LookupAutocomplete';

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

export const StatoSelect = memo(({ id, label, value, stati, onChange, required }: StatoSelectProps) => {
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
  // BUG-10: resetting query here used to fall `options` back to the default
  // unfiltered slice(0, 20) (see the useMemo above), which drops the just-
  // selected item whenever it isn't alphabetically within the first 20 —
  // e.g. selecting "ITALIA" then seeing the raw code "100000100" instead of
  // the name, because LookupAutocomplete's displayLabel looks the value up
  // in `options`. The input's displayed value is already driven by
  // `displayLabel`/`editValue` in LookupAutocomplete, not by `query`, so
  // resetting it here was never actually needed for the UI.
  const handleSelect = useCallback((codice: string) => { onChange(codice); }, [onChange]);

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
