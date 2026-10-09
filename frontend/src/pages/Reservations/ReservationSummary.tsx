import { useId, memo, type ReactNode, type Ref } from 'react';
import { useTranslation } from 'react-i18next';
import { M3Card } from '../../components/m3';
import { useFormatters } from '../../hooks/useFormatters';
import type { GuestResponseDTO, RoomResponse } from '../../types';
import { cn, nightsBetween } from '../../utils';

interface ReservationSummaryProps {
  /** `panel` is the compact sticky column next to the steps; `full` is the summary step / view page. */
  variant: 'panel' | 'full';
  checkInDate: string;
  checkOutDate: string;
  expectedGuests: number | string;
  rooms: RoomResponse[];
  selectedRoomIds: string[];
  /** roomId -> resolved total price for the selected dates (RatePricingService). */
  resolvedPrices: Map<string, number>;
  guest: GuestResponseDTO | null;
  /** Lets the page move focus to the title when the summary step opens. */
  headingRef?: Ref<HTMLHeadingElement>;
  /** Actions rendered at the bottom (e.g. the save button of the panel in edit mode). */
  children?: ReactNode;
}

const SectionTitle = ({ children }: { children: ReactNode }) => (
  <h3 className="text-xs font-medium uppercase tracking-wider text-on-surface-variant mb-1">{children}</h3>
);

export const ReservationSummary = memo(({
  variant,
  checkInDate,
  checkOutDate,
  expectedGuests,
  rooms,
  selectedRoomIds,
  resolvedPrices,
  guest,
  headingRef,
  children,
}: ReservationSummaryProps) => {
  const { t } = useTranslation('common');
  const { formatDate, formatCurrency } = useFormatters();
  const titleId = useId();

  const nights = nightsBetween(checkInDate, checkOutDate);
  const hasDates = !!checkInDate && !!checkOutDate;
  const selectedRooms = rooms.filter((room) => selectedRoomIds.includes(room.id));
  const allPriced = selectedRooms.length > 0 && selectedRooms.every((room) => resolvedPrices.has(room.id));
  const total = allPriced
    ? selectedRooms.reduce((sum, room) => sum + (resolvedPrices.get(room.id) ?? 0), 0)
    : null;

  return (
    <M3Card
      variant="solid"
      role="region"
      aria-labelledby={titleId}
      className={cn('p-5 space-y-4', variant === 'panel' && 'lg:sticky lg:top-4')}
    >
      <h2 id={titleId} ref={headingRef} tabIndex={-1} className="text-lg font-medium text-on-surface focus:outline-none">{t('summary_title')}</h2>

      <div>
        <SectionTitle>{t('summary_stay')}</SectionTitle>
        <p className="text-sm text-on-surface">
          {hasDates ? `${formatDate(checkInDate)} → ${formatDate(checkOutDate)}` : '—'}
        </p>
        <p className="text-sm text-on-surface-variant">
          {nights !== null && nights > 0 ? `${t('summary_nights', { count: nights })} · ` : ''}
          {t('label_expected_guests')}: {expectedGuests || '—'}
        </p>
      </div>

      <div>
        <SectionTitle>{t('summary_rooms')}</SectionTitle>
        {selectedRooms.length === 0 ? (
          <p className="text-sm text-on-surface-variant">{t('summary_no_rooms')}</p>
        ) : (
          <ul className="space-y-1">
            {selectedRooms.map((room) => (
              <li key={room.id} className="flex justify-between gap-3 text-sm text-on-surface">
                <span>
                  {t('room_number', { number: room.roomNumber })}
                  <span className="text-on-surface-variant"> · {room.roomType?.name || room.type}</span>
                </span>
                {resolvedPrices.has(room.id) && <span>{formatCurrency(resolvedPrices.get(room.id))}</span>}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <SectionTitle>{t('summary_guest')}</SectionTitle>
        {guest ? (
          <>
            <p className="text-sm text-on-surface">{guest.firstName} {guest.lastName}</p>
            {variant === 'full' && (guest.email || guest.phone) && (
              <p className="text-sm text-on-surface-variant">
                {[guest.email, guest.phone].filter(Boolean).join(' · ')}
              </p>
            )}
          </>
        ) : (
          <p className="text-sm text-on-surface-variant">{t('summary_no_guest')}</p>
        )}
      </div>

      {selectedRooms.length > 0 && (
        <div className="border-t border-outline-variant pt-3">
          {total !== null ? (
            <>
              <p className="flex justify-between text-base font-medium text-on-surface">
                <span>{t('summary_estimated_total')}</span>
                <span>{formatCurrency(total)}</span>
              </p>
              <p className="mt-1 text-xs text-on-surface-variant">{t('summary_price_note')}</p>
            </>
          ) : (
            <p className="text-sm text-on-surface-variant">{t('summary_price_on_confirm')}</p>
          )}
        </div>
      )}

      {children}
    </M3Card>
  );
});

ReservationSummary.displayName = 'ReservationSummary';
