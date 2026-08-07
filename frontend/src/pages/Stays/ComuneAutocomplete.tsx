import { useState, useCallback, memo, useRef } from 'react';
import { stayService } from '../../services/stayService';
import type { LookupOption } from './LookupOptionItem';
import { LookupAutocomplete } from './LookupAutocomplete';

// ---------------------------------------------------------------------------
// ComuneAutocomplete — server-side autocomplete for comuni
// ---------------------------------------------------------------------------

const AUTOCOMPLETE_DEBOUNCE_MS = 300;

interface ComuneAutocompleteProps {
  id: string;
  label: string;
  value: string;
  onSelect: (codice: string) => void;
  required?: boolean;
}

export const ComuneAutocomplete = memo(({ id, label, value, onSelect, required }: ComuneAutocompleteProps) => {
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
