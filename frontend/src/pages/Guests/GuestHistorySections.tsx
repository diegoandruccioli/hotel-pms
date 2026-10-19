import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Alert } from '../../components/Alert';
import { M3LoadingState } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { useFormatters } from '../../hooks';
import { useGuestInvoiceHistory, useGuestStayHistory, useRoomsLookup } from '../../hooks/queries';
import { cn, EMPTY_PLACEHOLDER, invoiceStatusTone, stayStatusTone } from '../../utils';

const HISTORY_LIMIT = 5;

interface SectionProps {
  heading: string;
  children: React.ReactNode;
}

export const DetailSection = ({ heading, children }: SectionProps) => (
  <section className="space-y-2">
    <h3 className="text-xs font-semibold uppercase tracking-wide text-on-surface-variant">{heading}</h3>
    {children}
  </section>
);

/** Stretches a row's link over the whole row (and its chip) without nesting any other control in it. */
const STRETCHED_LINK =
  'after:absolute after:inset-0 rounded-shape-sm focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary';
const ROW_CLASS = 'relative flex items-center justify-between gap-3 py-2';

const MoreItems = ({ total }: { total: number }) => {
  const { t } = useTranslation('guests');
  return total > HISTORY_LIMIT ? (
    <p className="text-xs text-on-surface-variant">{t('msg_more_items', { count: total - HISTORY_LIMIT })}</p>
  ) : null;
};

interface RowLabelProps {
  /** Without a target the row is plain text (a walk-in stay has no reservation to open). */
  to?: string;
  /** Billing opens this invoice's detail on arrival (router state, not a query param). */
  invoiceId?: string;
  children: React.ReactNode;
}

const RowLabel = ({ to, invoiceId, children }: RowLabelProps) => {
  const state = useMemo(() => (invoiceId ? { invoiceId } : undefined), [invoiceId]);
  return to ? (
    <Link to={to} state={state} className={cn('block min-w-0', STRETCHED_LINK)}>{children}</Link>
  ) : (
    <div className="min-w-0">{children}</div>
  );
};

interface HistoryProps {
  guestId: string;
}

export const StayHistorySection = ({ guestId }: HistoryProps) => {
  const { t } = useTranslation(['guests', 'common']);
  const { formatDate } = useFormatters();
  const { data, isLoading, isError } = useGuestStayHistory(guestId);
  const { data: rooms } = useRoomsLookup();
  const roomNumbers = useMemo(() => new Map((rooms ?? []).map((r) => [r.id, r.roomNumber])), [rooms]);

  return (
    <DetailSection heading={t('section_stay_history')}>
      {isLoading ? (
        <M3LoadingState plain label={t('common:loading')} />
      ) : isError ? (
        <Alert tone="error" compact>{t('err_history_load')}</Alert>
      ) : !data?.length ? (
        <p className="text-sm text-on-surface-variant">{t('msg_no_stays')}</p>
      ) : (
        <>
          <ul className="divide-y divide-outline-variant">
            {data.slice(0, HISTORY_LIMIT).map((stay) => (
              <li key={stay.stayId} className={cn(ROW_CLASS, stay.reservationId && 'hover:bg-surface-container-low')}>
                <RowLabel to={stay.reservationId ? `/reservations/${stay.reservationId}` : undefined}>
                  <p className="text-sm text-on-surface">
                    {formatDate(stay.checkInTime)} – {formatDate(stay.checkOutTime)}
                  </p>
                  <p className="text-xs text-on-surface-variant">
                    {t('label_room', { number: roomNumbers.get(stay.roomId) ?? EMPTY_PLACEHOLDER })}
                  </p>
                </RowLabel>
                <M3StatusChip
                  label={t(`common:status_${stay.status.toLowerCase()}`, stay.status)}
                  tone={stayStatusTone[stay.status]}
                />
              </li>
            ))}
          </ul>
          <MoreItems total={data.length} />
        </>
      )}
    </DetailSection>
  );
};

export const InvoiceHistorySection = ({ guestId }: HistoryProps) => {
  const { t } = useTranslation(['guests', 'common']);
  const { formatDate, formatCurrency } = useFormatters();
  const { data, isLoading, isError } = useGuestInvoiceHistory(guestId);

  return (
    <DetailSection heading={t('section_invoice_history')}>
      {isLoading ? (
        <M3LoadingState plain label={t('common:loading')} />
      ) : isError ? (
        <Alert tone="error" compact>{t('err_history_load')}</Alert>
      ) : !data?.length ? (
        <p className="text-sm text-on-surface-variant">{t('msg_no_invoices')}</p>
      ) : (
        <>
          <ul className="divide-y divide-outline-variant">
            {data.slice(0, HISTORY_LIMIT).map((inv) => (
              <li key={inv.invoiceId} className={cn(ROW_CLASS, 'hover:bg-surface-container-low')}>
                <RowLabel to="/billing" invoiceId={inv.invoiceId}>
                  <p className="text-sm text-on-surface">{inv.invoiceNumber}</p>
                  <p className="text-xs text-on-surface-variant">
                    {formatDate(inv.issueDate)} · {formatCurrency(inv.totalAmount)}
                  </p>
                </RowLabel>
                <M3StatusChip
                  label={t(`common:invoice_status_${inv.status}`, inv.status)}
                  tone={invoiceStatusTone[inv.status]}
                />
              </li>
            ))}
          </ul>
          <MoreItems total={data.length} />
        </>
      )}
    </DetailSection>
  );
};
