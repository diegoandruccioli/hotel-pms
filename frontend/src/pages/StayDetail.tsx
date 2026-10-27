import { useState, useCallback, memo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import type { StayResponse } from '../types';
import { PageHeader } from '../components/PageHeader';
import { M3Button, M3Card, M3ErrorState, M3LoadingState, M3StatusChip } from '../components/m3';
import { AlloggiatiCell } from './Stays/StayRowCells';
import { StayGuestManagerDialog } from './Stays/StayGuestManagerDialog';
import { StayExtensionDialog } from './Stays/StayExtensionDialog';
import { StayRoomChangeDialog } from './Stays/StayRoomChangeDialog';
import { getErrorMessage, stayStatusTone } from '../utils';
import { queryKeys } from '../lib';
import { useToastStore } from '../store';
import { useFormatters } from '../hooks';
import {
  useStayDetail,
  useCheckOutStay,
  useRetryInvoiceCreation,
  useRetryCheckoutEmail,
} from '../hooks/queries';

const LINK_CLASS =
  'text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs';

/** One stay, reached from a reservation or the stays list; the same actions as the list row, on their own page. */
export const StayDetail = memo(() => {
  const { t } = useTranslation('common');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const addToast = useToastStore((s) => s.addToast);
  const { formatDate } = useFormatters();

  const { data: stay, isLoading, error: queryError, refetch } = useStayDetail(id ?? null);
  const error = queryError ? getErrorMessage(queryError, t('failed_load_stays')) : null;

  const [managingGuests, setManagingGuests] = useState(false);
  const [extending, setExtending] = useState(false);
  const [changingRoom, setChangingRoom] = useState(false);

  const checkOutMutation = useCheckOutStay();
  const retryInvoiceMutation = useRetryInvoiceCreation();
  const retryEmailMutation = useRetryCheckoutEmail();

  const handleBack = useCallback(() => {
    if (location.key !== 'default') navigate(-1);
    else navigate('/stays');
  }, [location.key, navigate]);

  const handleCheckOut = useCallback(async () => {
    if (!stay) return;
    try {
      await checkOutMutation.mutateAsync(stay.id);
      addToast(t('guest_checked_out_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('checkout_failed')), 'error');
    }
  }, [stay, checkOutMutation, addToast, t]);

  const handleRetryInvoice = useCallback(async (target: StayResponse) => {
    try {
      await retryInvoiceMutation.mutateAsync(target.id);
      addToast(t('invoice_retry_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('invoice_retry_failed')), 'error');
    }
  }, [retryInvoiceMutation, addToast, t]);

  const handleRetryEmail = useCallback(async (target: StayResponse) => {
    try {
      await retryEmailMutation.mutateAsync(target.id);
      addToast(t('checkout_email_retry_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('checkout_email_retry_failed')), 'error');
    }
  }, [retryEmailMutation, addToast, t]);

  // Extension and room change answer with the updated stay: show it at once, then refresh the lists.
  const handleStayUpdated = useCallback((updated: StayResponse) => {
    queryClient.setQueryData(queryKeys.stays.detail(updated.id), updated);
    queryClient.invalidateQueries({ queryKey: queryKeys.stays.all });
  }, [queryClient]);

  const handleOpenGuests = useCallback(() => setManagingGuests(true), []);
  const handleCloseGuests = useCallback(() => setManagingGuests(false), []);
  const handleOpenExtension = useCallback(() => setExtending(true), []);
  const handleCloseExtension = useCallback(() => setExtending(false), []);
  const handleOpenRoomChange = useCallback(() => setChangingRoom(true), []);
  const handleCloseRoomChange = useCallback(() => setChangingRoom(false), []);

  const handleOpenReservation = useCallback(() => {
    if (stay?.reservationId) navigate(`/reservations/${stay.reservationId}`);
  }, [navigate, stay]);
  const handleOpenGuest = useCallback(() => {
    if (stay) navigate('/guests', { state: { guestId: stay.guestId } });
  }, [navigate, stay]);

  if (isLoading) return <M3LoadingState label={t('loading')} />;
  if (error || !stay) {
    return (
      <M3ErrorState
        title={t('error_loading_stays')}
        message={error ?? t('stay_not_found')}
        retryLabel={t('try_again')}
        onRetry={refetch}
      />
    );
  }

  const checkedIn = stay.status === 'CHECKED_IN';
  const guestName = stay.guestDisplayName ?? `${stay.guestId.substring(0, 8)}…`;
  const roomLabel = stay.roomNumber ?? `${stay.roomId.substring(0, 8)}…`;
  const guests = stay.guests ?? [];
  const checkingOut = checkOutMutation.isPending;

  return (
    <div className="space-y-6">
      <PageHeader
        icon="hotel"
        title={`${roomLabel} · ${guestName}`}
        crumbLabel={roomLabel}
        titleAdornment={
          <M3StatusChip
            label={t(`status_${stay.status.toLowerCase()}`, stay.status.replace('_', ' '))}
            tone={stayStatusTone[stay.status]}
            className="shrink-0 whitespace-nowrap"
          />
        }
        onBack={handleBack}
        actionsClassName="flex-wrap"
        actions={checkedIn ? (
          <>
            <M3Button
              variant="tonal"
              icon="logout"
              loading={checkingOut}
              disabled={checkingOut}
              onClick={handleCheckOut}
            >
              {t('action_checkout')}
            </M3Button>
            <M3Button variant="outlined" icon="event_repeat" onClick={handleOpenExtension}>
              {t('action_extend_stay')}
            </M3Button>
            <M3Button variant="outlined" icon="meeting_room" onClick={handleOpenRoomChange}>
              {t('action_change_room')}
            </M3Button>
          </>
        ) : undefined}
      />

      <M3Card variant="solid" className="p-6 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
        <div>
          <p className="text-on-surface-variant">{t('check_in')}</p>
          <p className="font-medium">{formatDate(stay.actualCheckInTime)}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('expected_checkout_col')}</p>
          <p className="font-medium">{formatDate(stay.expectedCheckOutDate)}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('check_out')}</p>
          <p className="font-medium">{formatDate(stay.actualCheckOutTime)}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('guests')}</p>
          <p className="font-medium">{guests.length}</p>
        </div>
      </M3Card>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <M3Card variant="solid" className="p-6 space-y-3 text-sm">
          <h2 className="text-base font-medium">{t('guests')}</h2>
          {guests.length === 0 ? (
            <p className="text-on-surface-variant">{t('stay_no_guests')}</p>
          ) : (
            <ul className="space-y-1">
              {guests.map((g) => (
                <li key={g.id} className="flex items-center gap-2">
                  <span>{g.lastName} {g.firstName}</span>
                  {g.isPrimaryGuest && <M3StatusChip label={t('stay_primary_guest')} tone="info" />}
                </li>
              ))}
            </ul>
          )}
          {checkedIn && (
            <M3Button variant="text" icon="group" onClick={handleOpenGuests}>{t('stay_manage_guests')}</M3Button>
          )}
        </M3Card>

        <M3Card variant="solid" className="p-6 space-y-3 text-sm">
          <h2 className="text-base font-medium">{t('stay_dispatch_status')}</h2>
          <AlloggiatiCell
            stay={stay}
            onRetryInvoice={handleRetryInvoice}
            retryingInvoice={retryInvoiceMutation.isPending ? (retryInvoiceMutation.variables ?? null) : null}
            onRetryCheckoutEmail={handleRetryEmail}
            retryingEmail={retryEmailMutation.isPending ? (retryEmailMutation.variables ?? null) : null}
            t={t}
          />
        </M3Card>
      </div>

      <M3Card variant="solid" className="p-6 space-y-2 text-sm">
        <h2 className="text-base font-medium">{t('stay_links')}</h2>
        <ul className="space-y-1">
          {stay.reservationId && (
            <li>
              <button type="button" onClick={handleOpenReservation} className={LINK_CLASS}>
                {t('stays_open_reservation')}
              </button>
            </li>
          )}
          <li>
            <button type="button" onClick={handleOpenGuest} className={LINK_CLASS}>
              {t('stay_open_guest')}
            </button>
          </li>
        </ul>
      </M3Card>

      <StayGuestManagerDialog stayId={managingGuests ? stay.id : null} onClose={handleCloseGuests} />
      <StayExtensionDialog stay={extending ? stay : null} onClose={handleCloseExtension} onExtended={handleStayUpdated} />
      <StayRoomChangeDialog stay={changingRoom ? stay : null} onClose={handleCloseRoomChange} onChanged={handleStayUpdated} />
    </div>
  );
});

StayDetail.displayName = 'StayDetail';
