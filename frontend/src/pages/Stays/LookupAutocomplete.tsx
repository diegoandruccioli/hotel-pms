import { useState, useCallback, memo, useRef, useMemo, useEffect } from 'react';
import type { ChangeEvent } from 'react';
import type { LookupOption } from './LookupOptionItem';
import { LookupOptionItem, AUTOCOMPLETE_MIN_CHARS } from './LookupOptionItem';

// ---------------------------------------------------------------------------
// LookupAutocomplete — server-side typeahead for Alloggiati Web lookup tables
// ---------------------------------------------------------------------------

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

export const LookupAutocomplete = memo(({
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
