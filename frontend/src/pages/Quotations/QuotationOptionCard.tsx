import { useMemo, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import type { QuotationOptionResponse } from '../../types';
import { M3Button, M3Card, M3StatusChip, M3Table, M3TableRow, M3TableCell } from '../../components/m3';
import { useFormatters } from '../../hooks';
import { cn } from '../../utils';

export const QuotationOptionCard = ({ option, isAccepted, isConvertChoice, selectable, onChoose }: {
  option: QuotationOptionResponse;
  isAccepted: boolean;
  isConvertChoice: boolean;
  selectable: boolean;
  onChoose?: (optionId: string) => void;
}) => {
  const { formatCurrency } = useFormatters();
  const { t } = useTranslation(['quotations', 'common']);
  const handleChoose = useCallback(() => onChoose?.(option.id), [onChoose, option.id]);

  const lineItemHeaders = useMemo(() => [
    <span key="room" className="sr-only">{t('common:room_number_col')}</span>,
    <span key="type" className="sr-only">{t('common:room_type')}</span>,
    <span key="price" className="sr-only">{t('common:amount')}</span>,
  ], [t]);

  return (
    <M3Card
      variant="outlined"
      className={cn(
        'p-4 space-y-3',
        isAccepted && 'border-tertiary border-2',
        isConvertChoice && 'border-primary border-2'
      )}
    >
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-on-surface">{option.label}</h3>
        {isAccepted && <M3StatusChip label={t('label_accepted_option')} tone="success" icon="check_circle" />}
      </div>
      <M3Table headers={lineItemHeaders}>
        {option.lineItems.map((li) => (
          <M3TableRow key={li.id}>
            <M3TableCell className="py-1.5 first:pl-0 last:pr-0 text-on-surface">{li.roomNumber}</M3TableCell>
            <M3TableCell className="py-1.5 first:pl-0 last:pr-0 text-on-surface-variant">{li.roomTypeName}</M3TableCell>
            <M3TableCell className="py-1.5 first:pl-0 last:pr-0 text-right text-on-surface-variant">{formatCurrency(li.price)}</M3TableCell>
          </M3TableRow>
        ))}
      </M3Table>
      <p className="text-right font-medium text-on-surface">{formatCurrency(option.totalPrice)}</p>
      {selectable && (
        <M3Button type="button" variant={isConvertChoice ? 'filled' : 'outlined'} onClick={handleChoose} className="w-full">
          {isConvertChoice ? t('common:selected') : t('action_choose_option')}
        </M3Button>
      )}
    </M3Card>
  );
};
