import { useCallback, memo } from 'react';

// ---------------------------------------------------------------------------
// LookupOptionItem — a single row in the LookupAutocomplete listbox.
// Also the canonical owner of `LookupOption` and `AUTOCOMPLETE_MIN_CHARS`:
// every component in the Lookup* family (LookupAutocomplete, StatoSelect,
// ComuneAutocomplete) imports them from here.
// ---------------------------------------------------------------------------

export interface LookupOption { codice: string; label: string; }

export const AUTOCOMPLETE_MIN_CHARS = 2;

interface LookupOptionItemProps {
  option: LookupOption;
  selected: boolean;
  onSelect: (opt: LookupOption) => void;
}

export const LookupOptionItem = memo(({ option, selected, onSelect }: LookupOptionItemProps) => {
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
