import { useState, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import type { GroupCheckoutOutcome, GroupMemberResponse } from '../types';
import { MaterialIcon } from '../components/MaterialIcon';
import { M3Button } from '../components/m3';
import { M3Card } from '../components/m3';
import { M3StatusChip } from '../components/m3';
import { M3Dialog } from '../components/m3';
import { M3LoadingState } from '../components/m3';
import { M3ErrorState } from '../components/m3';
import { useTranslation } from 'react-i18next';
import { getErrorMessage } from '../utils';
import { useToastStore } from '../store';
import {
  useReservationGroup, useCancelReservationGroup, useCheckoutReservationGroup,
} from '../hooks/queries';

const getStatusTone = (status: string) => {
  switch (status) {
    case 'CHECKED_OUT': return 'success' as const;
    case 'CHECKED_IN': return 'warning' as const;
    case 'CANCELLED': return 'error' as const;
    default: return 'neutral' as const;
  }
};

export const ReservationGroupDetail = () => {
  const { t, i18n } = useTranslation('common');
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);

  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [confirmingCheckout, setConfirmingCheckout] = useState(false);
  const [checkoutOutcomes, setCheckoutOutcomes] = useState<GroupCheckoutOutcome[] | null>(null);

  const { data: group, isLoading, error: queryError, refetch } = useReservationGroup(id);
  const cancelGroup = useCancelReservationGroup();
  const checkoutGroup = useCheckoutReservationGroup();
  const error = queryError ? getErrorMessage(queryError, t('group_load_failed')) : null;

  const formatCurrency = useCallback((amount: number | null) =>
    amount === null
      ? '—'
      : new Intl.NumberFormat(i18n.language, { style: 'currency', currency: 'EUR' }).format(amount),
  [i18n.language]);

  const getMemberStatusLabel = useCallback(
    (status: string) => t(`status_${status.toLowerCase()}`, status),
    [t],
  );

  const handleCancelRequest = useCallback(() => setConfirmingCancel(true), []);
  const handleCancelDialogClose = useCallback(() => setConfirmingCancel(false), []);
  const handleCancelConfirm = useCallback(async () => {
    if (!id || !group) return;
    try {
      await cancelGroup.mutateAsync({ id, version: group.version });
      addToast(t('group_cancelled_success'), 'success');
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('group_cancel_failed')), 'error');
    } finally {
      setConfirmingCancel(false);
    }
  }, [id, group, cancelGroup, addToast, t]);

  const handleCheckoutRequest = useCallback(() => setConfirmingCheckout(true), []);
  const handleCheckoutDialogClose = useCallback(() => {
    setConfirmingCheckout(false);
    setCheckoutOutcomes(null);
  }, []);
  const handleCheckoutConfirm = useCallback(async () => {
    if (!id) return;
    try {
      const outcomes = await checkoutGroup.mutateAsync(id);
      setCheckoutOutcomes(outcomes);
      const failures = outcomes.filter((o) => !o.success).length;
      if (failures === 0) {
        addToast(t('group_checkout_success'), 'success');
      } else {
        addToast(t('group_checkout_partial', { count: failures }), 'error');
      }
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('group_checkout_failed')), 'error');
      setConfirmingCheckout(false);
    }
  }, [id, checkoutGroup, addToast, t]);

  const handleBackToList = useCallback(() => navigate('/reservations/groups'), [navigate]);

  const canCancel = useMemo(
    () => !!group && group.status !== 'CANCELLED' && group.status !== 'CHECKED_OUT', [group]);
  const canCheckout = useMemo(
    () => !!group && group.members.some((m) => m.status === 'CHECKED_IN'), [group]);

  if (isLoading) return <M3LoadingState label={t('loading')} />;
  if (error || !group) {
    return (
      <M3ErrorState
        title={t('group_load_failed')}
        message={error ?? t('group_not_found')}
        retryLabel={t('try_again')}
        onRetry={refetch}
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <button
            type="button"
            className="text-sm text-primary hover:underline flex items-center gap-1 mb-1"
            onClick={handleBackToList}
          >
            <MaterialIcon name="arrow_back" size={16} />
            {t('nav_reservation_groups')}
          </button>
          <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface flex items-center gap-2">
            <MaterialIcon name="groups" className="text-primary" />
            {group.name}
            <M3StatusChip
              label={t(`group_status_${group.status.toLowerCase()}`)}
              tone={getStatusTone(group.status)}
            />
          </h1>
        </div>
        <div className="flex items-center gap-2">
          {canCheckout && (
            <M3Button variant="tonal" icon="logout" onClick={handleCheckoutRequest}>
              {t('checkout_group')}
            </M3Button>
          )}
          {canCancel && (
            <M3Button variant="outlined" icon="cancel" onClick={handleCancelRequest}>
              {t('cancel_group')}
            </M3Button>
          )}
        </div>
      </div>

      <M3Card className="p-6 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4 text-sm">
        <div>
          <p className="text-on-surface-variant">{t('company_name')}</p>
          <p className="font-medium">{group.companyName ?? '—'}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('contact_guest')}</p>
          <p className="font-medium">{group.contactGuestName}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('label_checkin_date')} / {t('label_checkout_date')}</p>
          <p className="font-medium">{group.checkInDate} — {group.checkOutDate}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('group_rate_per_night')}</p>
          <p className="font-medium">{formatCurrency(group.groupRatePerNight)}</p>
        </div>
        <div>
          <p className="text-on-surface-variant">{t('master_folio')}</p>
          <p className="font-medium">
            {group.masterFolioInvoiceId ? t('master_folio_open') : t('master_folio_none')}
          </p>
        </div>
        {group.notes && (
          <div className="sm:col-span-2 md:col-span-3">
            <p className="text-on-surface-variant">{t('notes')}</p>
            <p className="font-medium whitespace-pre-wrap">{group.notes}</p>
          </div>
        )}
      </M3Card>

      <M3Card className="p-6 space-y-4">
        <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider">
          {t('rooming_list')}
        </h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-on-surface-variant border-b border-outline-variant">
                <th className="py-2 pr-4">{t('guest')}</th>
                <th className="py-2 pr-4">{t('label_expected_guests')}</th>
                <th className="py-2 pr-4">{t('status')}</th>
                <th className="py-2 pr-4">{t('billed_to_master_folio')}</th>
                <th className="py-2 pr-4">{t('amount')}</th>
              </tr>
            </thead>
            <tbody>
              {group.members.map((member: GroupMemberResponse) => (
                <tr key={member.reservationId} className="border-b border-outline-variant last:border-0">
                  <td className="py-2 pr-4 font-medium">{member.guestFullName}</td>
                  <td className="py-2 pr-4">{member.expectedGuests}</td>
                  <td className="py-2 pr-4">
                    <M3StatusChip
                      label={getMemberStatusLabel(member.status)}
                      tone={member.status === 'CANCELLED' || member.status === 'NO_SHOW' ? 'error' : 'neutral'}
                    />
                  </td>
                  <td className="py-2 pr-4">
                    {member.billedToMasterFolio
                      ? <MaterialIcon name="check" size={16} className="text-primary" />
                      : '—'}
                  </td>
                  <td className="py-2 pr-4">{formatCurrency(member.price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </M3Card>

      {confirmingCancel && (
        <M3Dialog open title={t('cancel_group')} titleId="confirm-cancel-group-dialog" onClose={handleCancelDialogClose}>
          <p className="text-sm font-body text-on-surface">{t('cancel_group_confirm')}</p>
          <div className="flex justify-end gap-3 pt-4">
            <M3Button type="button" variant="outlined" onClick={handleCancelDialogClose} disabled={cancelGroup.isPending}>
              {t('cancel')}
            </M3Button>
            <M3Button type="button" onClick={handleCancelConfirm} loading={cancelGroup.isPending}>
              {t('confirm')}
            </M3Button>
          </div>
        </M3Dialog>
      )}

      {confirmingCheckout && (
        <M3Dialog open title={t('checkout_group')} titleId="confirm-checkout-group-dialog" onClose={handleCheckoutDialogClose}>
          {checkoutOutcomes ? (
            <div className="space-y-3">
              <ul className="space-y-1 text-sm">
                {checkoutOutcomes.map((outcome) => (
                  <li key={outcome.reservationId} className="flex items-center gap-2">
                    <MaterialIcon
                      name={outcome.success ? 'check_circle' : 'error'}
                      size={16}
                      className={outcome.success ? 'text-success' : 'text-error'}
                    />
                    <span>{outcome.success ? t('checkout_room_success') : (outcome.errorCode ?? t('checkout_room_failed'))}</span>
                  </li>
                ))}
              </ul>
              <div className="flex justify-end pt-2">
                <M3Button type="button" onClick={handleCheckoutDialogClose}>{t('close')}</M3Button>
              </div>
            </div>
          ) : (
            <>
              <p className="text-sm font-body text-on-surface">{t('checkout_group_confirm')}</p>
              <div className="flex justify-end gap-3 pt-4">
                <M3Button type="button" variant="outlined" onClick={handleCheckoutDialogClose} disabled={checkoutGroup.isPending}>
                  {t('cancel')}
                </M3Button>
                <M3Button type="button" onClick={handleCheckoutConfirm} loading={checkoutGroup.isPending}>
                  {t('confirm')}
                </M3Button>
              </div>
            </>
          )}
        </M3Dialog>
      )}
    </div>
  );
};
