import { useState, useEffect, useCallback, memo } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { M3Dialog } from '../../components/m3';
import { M3Button } from '../../components/m3';
import { M3StatusChip } from '../../components/m3';
import { M3TextField } from '../../components/m3';
import { M3LoadingState } from '../../components/m3';
import { MaterialIcon } from '../../components/MaterialIcon';
import { useToastStore } from '../../store/toastStore';
import { stayService } from '../../services/stayService';
import { getErrorMessage } from '../../utils';
import { queryKeys } from '../../lib';
import type {
  AlloggiatiStato,
  AlloggiatiTipdoc,
  StayGuestRequest,
  StayGuestResponse,
  StayResponse,
} from '../../types';
import { GuestFieldSection } from './GuestFieldSection';
import { emptyGuest, CODICE_ITALIA, TYPES_WITHOUT_DOC, alloggiatiPlaceIssues } from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

interface StayGuestManagerDialogProps {
  /** null closes the dialog. */
  stayId: string | null;
  onClose: () => void;
}

const toIdentifiableGuest = (g: StayGuestResponse, stati: AlloggiatiStato[]): IdentifiableGuest => {
  const isStatoCode = (code: string) => stati.some((s) => s.codice === code);
  return {
    _id: g.id,
    firstName: g.firstName,
    lastName: g.lastName,
    gender: g.gender,
    dateOfBirth: g.dateOfBirth,
    placeOfBirth: g.placeOfBirth,
    citizenship: g.citizenship,
    documentType: g.documentType ?? '',
    documentNumber: g.documentNumber ?? '',
    documentPlaceOfIssue: g.documentPlaceOfIssue ?? '',
    isPrimaryGuest: g.isPrimaryGuest,
    travellerType: g.travellerType,
    travelPurpose: g.travelPurpose ?? '',
    version: g.version,
    _statoDiNascita: isStatoCode(g.placeOfBirth) ? g.placeOfBirth : CODICE_ITALIA,
    _statoRilascioDoc: g.documentPlaceOfIssue && isStatoCode(g.documentPlaceOfIssue) ? g.documentPlaceOfIssue : CODICE_ITALIA,
  };
};

const toRequest = (g: IdentifiableGuest): StayGuestRequest => ({
  firstName: g.firstName,
  lastName: g.lastName,
  gender: g.gender,
  dateOfBirth: g.dateOfBirth,
  placeOfBirth: g.placeOfBirth,
  citizenship: g.citizenship,
  documentType: g.documentType || undefined,
  documentNumber: g.documentNumber || undefined,
  documentPlaceOfIssue: g.documentPlaceOfIssue || undefined,
  isPrimaryGuest: g.isPrimaryGuest,
  travellerType: g.travellerType,
  travelPurpose: g.travelPurpose || undefined,
  version: g.version,
});

type ErrorTranslator = (key: string, options?: Record<string, unknown>) => string;

interface GuestRowProps {
  guest: StayGuestResponse;
  t: ErrorTranslator;
  busyGuestId: string | null;
  confirmRemoveId: string | null;
  departureTargetId: string | null;
  departureDate: string;
  onEdit: (guest: StayGuestResponse) => void;
  onStartDeparture: (id: string) => void;
  onCancelDeparture: () => void;
  onConfirmDeparture: () => void;
  onDepartureDateChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onPromote: (id: string) => void;
  onRequestRemove: (id: string) => void;
  onCancelRemove: () => void;
  onConfirmRemove: (id: string) => void;
}

const GuestRow = memo(({
  guest, t, busyGuestId, confirmRemoveId, departureTargetId, departureDate,
  onEdit, onStartDeparture, onCancelDeparture, onConfirmDeparture, onDepartureDateChange,
  onPromote, onRequestRemove, onCancelRemove, onConfirmRemove,
}: GuestRowProps) => {
  const isBusy = busyGuestId === guest.id;
  const handleEdit = useCallback(() => onEdit(guest), [onEdit, guest]);
  const handleStartDeparture = useCallback(() => onStartDeparture(guest.id), [onStartDeparture, guest.id]);
  const handlePromote = useCallback(() => onPromote(guest.id), [onPromote, guest.id]);
  const handleRequestRemove = useCallback(() => onRequestRemove(guest.id), [onRequestRemove, guest.id]);
  const handleConfirmRemove = useCallback(() => onConfirmRemove(guest.id), [onConfirmRemove, guest.id]);

  return (
    <div className="border border-outline-variant rounded-shape-md p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <MaterialIcon name="person" size={18} className="text-on-surface-variant" />
          <span className="font-medium text-on-surface">{guest.lastName} {guest.firstName}</span>
          {guest.isPrimaryGuest && <M3StatusChip label={t('guest_badge_primary')} tone="neutral" />}
          {guest.alloggiatiSent && <M3StatusChip label={t('guest_badge_sent')} tone="success" />}
          {guest.needsResubmit && <M3StatusChip label={t('guest_badge_needs_resubmit')} tone="error" />}
          {guest.departureDate && (
            <M3StatusChip label={t('guest_badge_departed', { date: guest.departureDate })} tone="neutral" />
          )}
        </div>
        <span className="text-xs text-on-surface-variant">
          {t('guest_arrival_date_label')}: {guest.arrivalDate}
        </span>
      </div>

      {departureTargetId === guest.id ? (
        <div className="flex items-end gap-2 mt-3">
          <M3TextField
            label={t('label_departure_date')}
            type="date"
            value={departureDate}
            onChange={onDepartureDateChange}
          />
          <M3Button variant="tonal" onClick={onConfirmDeparture} loading={isBusy} disabled={isBusy}>
            {t('btn_confirm')}
          </M3Button>
          <M3Button variant="text" onClick={onCancelDeparture}>
            {t('btn_cancel')}
          </M3Button>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2 mt-3">
          <M3Button variant="text" icon="edit" onClick={handleEdit}>
            {t('btn_edit')}
          </M3Button>
          {!guest.departureDate && (
            <M3Button variant="text" icon="logout" onClick={handleStartDeparture}>
              {t('btn_record_departure')}
            </M3Button>
          )}
          {!guest.isPrimaryGuest && (
            <M3Button variant="text" icon="star" onClick={handlePromote} loading={isBusy} disabled={isBusy}>
              {t('btn_promote_primary')}
            </M3Button>
          )}
          {confirmRemoveId === guest.id ? (
            <>
              <span className="text-sm text-error self-center">{t('confirm_remove_guest')}</span>
              <M3Button variant="text" onClick={handleConfirmRemove} loading={isBusy} disabled={isBusy}>
                {t('btn_confirm')}
              </M3Button>
              <M3Button variant="text" onClick={onCancelRemove}>
                {t('btn_cancel')}
              </M3Button>
            </>
          ) : (
            <M3Button
              variant="text"
              icon="delete"
              onClick={handleRequestRemove}
              disabled={guest.alloggiatiSent || guest.isPrimaryGuest}
              title={guest.alloggiatiSent
                ? t('hint_remove_disabled_sent')
                : guest.isPrimaryGuest
                  ? t('hint_remove_disabled_primary')
                  : undefined}
            >
              {t('btn_remove')}
            </M3Button>
          )}
        </div>
      )}
    </div>
  );
});
GuestRow.displayName = 'GuestRow';

const validateSingleGuest = (g: IdentifiableGuest, t: ErrorTranslator): string | null => {
  const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as never);

  if (!g.firstName || !g.lastName || !g.gender || !g.dateOfBirth || !g.travellerType) {
    return t('err_required_fields');
  }
  if (hasDoc && (!g.documentType || !g.documentNumber)) {
    return t('err_required_fields');
  }
  // Stato/comune-di-nascita and stato/comune-di-rilascio-documento rules are the
  // same ones CheckInForm/WalkInCheckInForm enforce for every guest at check-in —
  // shared here rather than re-derived, so a future Alloggiati rule change only
  // needs to be made once (see alloggiatiPlaceIssues' own doc for why the
  // required-field and primary-guest checks above/below aren't also shared: they
  // don't apply the same way to a lone correction on an already-open stay).
  return alloggiatiPlaceIssues(g, t, 1)[0] ?? null;
};

export const StayGuestManagerDialog = memo(({ stayId, onClose }: StayGuestManagerDialogProps) => {
  const { t } = useTranslation(['stays', 'common']);
  const addToast = useToastStore((s) => s.addToast);
  const queryClient = useQueryClient();

  const [stay, setStay] = useState<StayResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [stati, setStati] = useState<AlloggiatiStato[]>([]);
  const [tipdoc, setTipdoc] = useState<AlloggiatiTipdoc[]>([]);

  const [formGuest, setFormGuest] = useState<IdentifiableGuest | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [confirmRemoveId, setConfirmRemoveId] = useState<string | null>(null);
  const [busyGuestId, setBusyGuestId] = useState<string | null>(null);
  const [departureTargetId, setDepartureTargetId] = useState<string | null>(null);
  const [departureDate, setDepartureDate] = useState('');

  const refreshStay = useCallback(async (id: string) => {
    const updated = await stayService.getStayById(id);
    setStay(updated);
    return updated;
  }, []);

  useEffect(() => {
    if (!stayId) return;
    let cancelled = false;
    setLoading(true);
    Promise.all([
      stayService.getStayById(stayId),
      stayService.getLookupStati(),
      stayService.getLookupTipdoc(),
    ]).then(([s, statiList, tipdocList]) => {
      if (cancelled) return;
      setStay(s);
      setStati(statiList);
      setTipdoc(tipdocList);
    }).catch((err: unknown) => {
      if (!cancelled) addToast(getErrorMessage(err, t('err_load_guests')), 'error');
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [stayId, addToast, t]);

  const closeAndReset = useCallback(() => {
    setStay(null);
    setFormGuest(null);
    setEditingId(null);
    setFormError(null);
    setConfirmRemoveId(null);
    setDepartureTargetId(null);
    queryClient.invalidateQueries({ queryKey: queryKeys.stays.all });
    onClose();
  }, [onClose, queryClient]);

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
      await refreshStay(stayId);
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
      await refreshStay(stayId);
    } catch (err: unknown) {
      addToast(getErrorMessage(err, t('err_remove_guest')), 'error');
    } finally {
      setBusyGuestId(null);
      setConfirmRemoveId(null);
    }
  }, [stayId, t, addToast, refreshStay]);

  const handleStartDeparture = useCallback((guestId: string) => {
    setDepartureTargetId(guestId);
    setDepartureDate(new Date().toISOString().slice(0, 10));
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
      await refreshStay(stayId);
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
      await refreshStay(stayId);
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
            <GuestRow
              key={guest.id}
              guest={guest}
              t={t}
              busyGuestId={busyGuestId}
              confirmRemoveId={confirmRemoveId}
              departureTargetId={departureTargetId}
              departureDate={departureDate}
              onEdit={handleOpenEdit}
              onStartDeparture={handleStartDeparture}
              onCancelDeparture={handleCancelDeparture}
              onConfirmDeparture={handleConfirmDeparture}
              onDepartureDateChange={handleDepartureDateChange}
              onPromote={handlePromote}
              onRequestRemove={handleRequestRemove}
              onCancelRemove={handleCancelRemove}
              onConfirmRemove={handleRemove}
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
    </M3Dialog>
  );
});
StayGuestManagerDialog.displayName = 'StayGuestManagerDialog';
