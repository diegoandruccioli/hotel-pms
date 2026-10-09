import { useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { M3TextField } from '../../components/m3';

interface StayDatesFieldsProps {
  checkInDate: string;
  checkOutDate: string;
  expectedGuests: number | string;
  onCheckInChange: (val: string) => void;
  onCheckOutChange: (val: string) => void;
  onExpectedGuestsChange: (val: number | string) => void;
  readOnly?: boolean;
}

export const StayDatesFields = memo(({
  checkInDate,
  checkOutDate,
  expectedGuests,
  onCheckInChange,
  onCheckOutChange,
  onExpectedGuestsChange,
  readOnly = false
}: StayDatesFieldsProps) => {
  const { t } = useTranslation(['reservations', 'common']);

  const handleCheckInChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onCheckInChange(e.target.value);
  }, [onCheckInChange]);

  const handleCheckOutChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    onCheckOutChange(e.target.value);
  }, [onCheckOutChange]);

  const handleExpectedGuestsChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const val = parseInt(e.target.value);
    onExpectedGuestsChange(isNaN(val) ? '' : val);
  }, [onExpectedGuestsChange]);

  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
      <M3TextField
        label={t('label_checkin_date')}
        type="date"
        value={checkInDate}
        onChange={handleCheckInChange}
        required
        readOnly={readOnly}
      />
      <M3TextField
        label={t('label_checkout_date')}
        type="date"
        value={checkOutDate}
        onChange={handleCheckOutChange}
        required
        readOnly={readOnly}
      />
      <M3TextField
        label={t('label_expected_guests')}
        type="number"
        min="1"
        value={expectedGuests}
        onChange={handleExpectedGuestsChange}
        required
        readOnly={readOnly}
      />
    </div>
  );
});

StayDatesFields.displayName = 'StayDatesFields';
