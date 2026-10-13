import { useState, useEffect, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { M3ConfirmDialog } from '../../components/m3';
import { M3Dialog } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3LoadingState } from '../../components/m3';
import { useToastStore } from '../../store';
import { stayService } from '../../services';
import { useAlloggiatiLookups, useStayDetail } from '../../hooks/queries';
import { getErrorMessage, todayIsoDate } from '../../utils';
import { queryKeys } from '../../lib';
import type { AlloggiatiStato, AlloggiatiTipdoc, StayGuestResponse } from '../../types';
import { GuestFieldSection } from './GuestFieldSection';
import { StayGuestRow } from './StayGuestRow';
import { emptyGuest, toIdentifiableGuest, toRequest, validateSingleGuest } from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

const EMPTY_STATI: AlloggiatiStato[] = [];
const EMPTY_TIPDOC: AlloggiatiTipdoc[] = [];

interface StayGuestManagerDialogProps {
  /** null closes the dialog. */
  stayId: string | null;
  onClose: () => void;
}

export const StayGuestManagerDialog = memo(({ stayId, onClose }: StayGuestManagerDialogProps) => {
  const { t } = useTranslation(['stays', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const stayQuery = useStayDetail(stayId);
  const lookups = useAlloggiatiLookups(!!stayId);
  const stay = stayQuery.data ?? null;
  const stati = lookups.stati.data ?? EMPTY_STATI;
  const tipdoc = lookups.tipdoc.data ?? EMPTY_TIPDOC;
  const loading = !!stayId && (stayQuery.isLoading || lookups.stati.isLoading || lookups.tipdoc.isLoading);
  const loadError = stayQuery.error ?? lookups.stati.error ?? lookups.tipdoc.error;

  const [formGuest, setFormGuest] = useState<IdentifiableGuest | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [confirmRemoveId, setConfirmRemoveId] = useState<string | null>(null);
  const [busyGuestId, setBusyGuestId] = useState<string | null>(null);
  const [departureTargetId, setDepartureTargetId] = useState<string | null>(null);
  const [departureDate, setDepartureDate] = useState('');

  const { refetch: refetchStay } = stayQuery;
  const refreshStay = useCallback(async () => { await refetchStay(); }, [refetchStay]);

  useEffect(() => {
    if (loadError) addToast(getErrorMessage(loadError, t('err_load_guests')), 'error');
  }, [loadError, addToast, t]);

  const closeAndReset = useCallback(() => {
    setFormGuest(null);
    setEditingId(null);
    setFormError(null);
    setConfirmRemoveId(null);
    setDepartureTargetId(null);
    if (stayId) queryClient.removeQueries({ queryKey: queryKeys.stays.detail(stayId) });
    queryClient.invalidateQueries({ queryKey: queryKeys.stays.all });
    onClose();
  }, [onClose, queryClient, stayId]);

  const handleOpenAdd = useCallback(() => {
    setFormGuest(emptyGuest(false));
    setEditingId(null);
    setFormError(null);
  }, []);

  const handleOpenEdit = useCallback((guest: StayGuestResponse) => {
    setFormGuest(toIdentifiableGuest(guest, stati));
    setEditingId(guest.id);
    setFormError(null);
  }, [stati]);

  const handleCancelForm = useCallback(() => {
    setFormGuest(null);
    setEditingId(null);
    setFormError(null);
  }, []);

  const handleFormChange = useCallback((_idx: number, patch: Partial<IdentifiableGuest>) => {
    setFormGuest((prev) => (prev ? { ...prev, ...patch } : prev));
  }, []);

  const handleSaveForm = useCallback(async () => {
    if (!formGuest || !stayId) return;
    const issue = validateSingleGuest(formGuest, t);
    if (issue) {
      setFormError(issue);
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (editingId) {
        await stayService.updateGuest(stayId, editingId, toRequest(formGuest));
        addToast(t('guest_updated_success'), 'success');
      } else {
        await stayService.addGuest(stayId, toRequest(formGuest));
        addToast(t('guest_added_success'), 'success');
      }
      await refreshStay();
      setFormGuest(null);
      setEditingId(null);
    } catch (err: unknown) {
      setFormError(getErrorMessage(err, t('err_save_guest')));
    } finally {
      setSaving(false);
    }
  }, [formGuest, stayId, editingId, t, addToast, refreshStay]);

  const handleRemove = useCallback(async (guestId: string) => {
    if (!stayId) return;
    setBusyGuestId(guestId);
    try {
      await stayService.removeGuest(stayId, guestId);
      addToast(t('guest_removed_success'), 'success');
      await refreshStay();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_remove_guest')), 'error');
    } finally {
      setBusyGuestId(null);
      setConfirmRemoveId(null);
    }
  }, [stayId, t, addToast, refreshStay]);

  const handleConfirmRemove = useCallback(() => {
    if (confirmRemoveId) void handleRemove(confirmRemoveId);
  }, [confirmRemoveId, handleRemove]);

  const handleStartDeparture = useCallback((guestId: string) => {
    setDepartureTargetId(guestId);
    setDepartureDate(todayIsoDate());
  }, []);

  const handleCancelDeparture = useCallback(() => setDepartureTargetId(null), []);
  const handleRequestRemove = useCallback((guestId: string) => setConfirmRemoveId(guestId), []);
  const handleCancelRemove = useCallback(() => setConfirmRemoveId(null), []);
  const handleNoOpRemove = useCallback(() => { /* single-guest form has no remove */ }, []);

  const handleConfirmDeparture = useCallback(async () => {
    if (!stayId || !departureTargetId || !departureDate) return;
    setBusyGuestId(departureTargetId);
    try {
      await stayService.recordGuestDeparture(stayId, departureTargetId, departureDate);
      addToast(t('guest_departure_success'), 'success');
      await refreshStay();
      setDepartureTargetId(null);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_record_departure')), 'error');
    } finally {
      setBusyGuestId(null);
    }
  }, [stayId, departureTargetId, departureDate, t, addToast, refreshStay]);

  const handlePromote = useCallback(async (guestId: string) => {
    if (!stayId) return;
    setBusyGuestId(guestId);
    try {
      await stayService.promoteGuestToPrimary(stayId, guestId);
      addToast(t('guest_promoted_success'), 'success');
      await refreshStay();
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_promote_guest')), 'error');
    } finally {
      setBusyGuestId(null);
    }
  }, [stayId, t, addToast, refreshStay]);

  const handleDepartureDateChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setDepartureDate(e.target.value),
    [],
  );

  if (!stayId) return null;

  const guests = stay?.guests ?? [];

  return (
    <M3Dialog
      open={!!stayId}
      title={t('guest_manager_title', { room: stay?.roomNumber ?? '' })}
      titleId="stay-guest-manager-title"
      onClose={closeAndReset}
    >
      {loading ? (
        <M3LoadingState label={t('loading')} />
      ) : (
        <div className="space-y-4">
          {!formGuest && guests.map((guest) => (
            <StayGuestRow
              key={guest.id}
              guest={guest}
              t={t}
              busyGuestId={busyGuestId}
              departureTargetId={departureTargetId}
              departureDate={departureDate}
              onEdit={handleOpenEdit}
              onStartDeparture={handleStartDeparture}
              onCancelDeparture={handleCancelDeparture}
              onConfirmDeparture={handleConfirmDeparture}
              onDepartureDateChange={handleDepartureDateChange}
              onPromote={handlePromote}
              onRequestRemove={handleRequestRemove}
            />
          ))}

          {formGuest ? (
            <div className="space-y-3">
              <GuestFieldSection
                guest={formGuest}
                index={0}
                canRemove={false}
                stati={stati}
                tipdoc={tipdoc}
                onRemove={handleNoOpRemove}
                onChange={handleFormChange}
              />
              {formError && <p className="text-sm text-error">{formError}</p>}
              <div className="flex justify-end gap-2">
                <M3Button variant="text" onClick={handleCancelForm} type="button">
                  {t('btn_cancel')}
                </M3Button>
                <M3Button onClick={handleSaveForm} loading={saving} disabled={saving} type="button">
                  {t('btn_save')}
                </M3Button>
              </div>
            </div>
          ) : (
            <M3Button icon="person_add" variant="outlined" onClick={handleOpenAdd} type="button">
              {t('btn_add_guest')}
            </M3Button>
          )}
        </div>
      )}

      {confirmRemoveId && (
        <M3ConfirmDialog
          title={t('confirm_remove_guest_title')}
          titleId="stay-guest-remove-title"
          message={t('confirm_remove_guest')}
          confirmLabel={t('btn_confirm')}
          cancelLabel={t('btn_cancel')}
          onConfirm={handleConfirmRemove}
          onCancel={handleCancelRemove}
          loading={busyGuestId === confirmRemoveId}
        />
      )}
    </M3Dialog>
  );
});
StayGuestManagerDialog.displayName = 'StayGuestManagerDialog';
