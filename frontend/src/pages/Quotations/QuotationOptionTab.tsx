import { memo, useCallback } from 'react';
import { MaterialIcon } from '../../components/MaterialIcon';
import { useFormatters } from '../../hooks';
import { cn } from '../../utils';
import { defaultOptionLabel } from './quotationDraft';
import type { OptionDraft } from './quotationDraft';

export const QuotationOptionTab = memo(({ option, index, isActive, canRemove, total, onSelect, onRemove }: {
  option: OptionDraft;
  index: number;
  isActive: boolean;
  canRemove: boolean;
  total: number;
  onSelect: (index: number) => void;
  onRemove: (index: number) => void;
}) => {
  const { formatCurrency } = useFormatters();
  const handleSelect = useCallback(() => onSelect(index), [onSelect, index]);
  const handleRemove = useCallback(() => onRemove(index), [onRemove, index]);
  return (
    <div className={cn('flex items-center rounded-shape-full border', isActive ? 'border-primary bg-primary-container' : 'border-outline-variant')}>
      <button
        type="button"
        onClick={handleSelect}
        aria-pressed={isActive}
        className={cn(
          'px-4 py-2 text-sm font-medium font-body rounded-shape-full focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary',
          isActive ? 'text-on-primary-container' : 'text-on-surface-variant'
        )}
      >
        {option.label || defaultOptionLabel(index)} · {formatCurrency(total)}
      </button>
      {canRemove && (
        <button
          type="button"
          onClick={handleRemove}
          aria-label={`Rimuovi ${option.label || defaultOptionLabel(index)}`}
          className="pr-3 pl-1 text-on-surface-variant hover:text-error focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-full"
        >
          <MaterialIcon name="close" size={16} />
        </button>
      )}
    </div>
  );
});
QuotationOptionTab.displayName = 'QuotationOptionTab';
