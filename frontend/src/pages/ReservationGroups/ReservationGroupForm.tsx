import { useState, useCallback, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Button, M3Card, M3TextField, M3Textarea, M3Checkbox } from '../../components/m3';
import { GuestSearchAndCreate } from '../Reservations/GuestSearchAndCreate';
import { RoomingListRow, type RoomingListRowState } from './RoomingListRow';
import { useCreateReservationGroup } from '../../hooks/queries';
import { inventoryService } from '../../services';
import { useToastStore } from '../../store';
import { getErrorMessage } from '../../utils';
import type { GuestResponseDTO, ReservationGroupCreateRequest, RoomResponse } from '../../types';

let rowIdCounter = 0;
const nextRowId = () => `row-${(rowIdCounter += 1)}`;

const newRow = (): RoomingListRowState => ({
  tempId: nextRowId(), guest: null, roomId: '', expectedGuests: 1, billedToMasterFolio: false,
});

export const ReservationGroupForm = () => {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const createGroup = useCreateReservationGroup();

  const [name, setName] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [contactGuest, setContactGuest] = useState<GuestResponseDTO | null>(null);
  const [checkInDate, setCheckInDate] = useState('');
  const [checkOutDate, setCheckOutDate] = useState('');
  const [groupRatePerNight, setGroupRatePerNight] = useState<number | string>('');
  const [notes, setNotes] = useState('');
  const [openMasterFolio, setOpenMasterFolio] = useState(false);
  const [rows, setRows] = useState<RoomingListRowState[]>([newRow()]);
  const [availableRooms, setAvailableRooms] = useState<RoomResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!checkInDate || !checkOutDate) {
      return;
    }
    let cancelled = false;
    inventoryService.getAvailableRooms(checkInDate, checkOutDate)
      .then((rooms) => { if (!cancelled) setAvailableRooms(rooms); })
      .catch(() => { if (!cancelled) setAvailableRooms([]); });
    return () => { cancelled = true; };
  }, [checkInDate, checkOutDate]);

  const handleAddRow = useCallback(() => setRows((prev) => [...prev, newRow()]), []);

  const handleRemoveRow = useCallback((tempId: string) => {
    setRows((prev) => prev.filter((r) => r.tempId !== tempId));
  }, []);

  const handleRowChange = useCallback((tempId: string, patch: Partial<RoomingListRowState>) => {
    setRows((prev) => prev.map((r) => (r.tempId === tempId ? { ...r, ...patch } : r)));
  }, []);

  const roomTakenElsewhere = useCallback(
    (roomId: string) => rows.some((r) => r.roomId === roomId),
    [rows],
  );

  const canSubmit = useMemo(() => {
    if (!name.trim() || !contactGuest || !checkInDate || !checkOutDate) return false;
    if (!(checkOutDate > checkInDate)) return false;
    return rows.length > 0 && rows.every((r) => r.guest && r.roomId && Number(r.expectedGuests) >= 1);
  }, [name, contactGuest, checkInDate, checkOutDate, rows]);

  const handleSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit || !contactGuest) return;
    setError(null);

    const request: ReservationGroupCreateRequest = {
      name: name.trim(),
      companyName: companyName.trim() || null,
      contactGuestId: contactGuest.id,
      checkInDate,
      checkOutDate,
      groupRatePerNight: groupRatePerNight === '' ? null : Number(groupRatePerNight),
      notes: notes.trim() || null,
      openMasterFolio,
      rooms: rows.map((r) => ({
        guestId: (r.guest as GuestResponseDTO).id,
        roomId: r.roomId,
        expectedGuests: Number(r.expectedGuests),
        billedToMasterFolio: openMasterFolio && r.billedToMasterFolio,
      })),
    };

    try {
      const created = await createGroup.mutateAsync(request);
      addToast(t('group_created_success'), 'success');
      navigate(`/reservations/groups/${created.id}`);
    } catch (err: unknown) {
      setError(getErrorMessage(err, t('group_create_failed')));
    }
  }, [canSubmit, contactGuest, name, companyName, checkInDate, checkOutDate, groupRatePerNight,
      notes, openMasterFolio, rows, createGroup, addToast, navigate, t]);

  const handleNameChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => setName(e.target.value), []);
  const handleCompanyChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setCompanyName(e.target.value), []);
  const handleCheckInChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setCheckInDate(e.target.value), []);
  const handleCheckOutChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setCheckOutDate(e.target.value), []);
  const handleRateChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    setGroupRatePerNight(e.target.value === '' ? '' : Number(e.target.value));
  }, []);
  const handleNotesChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => setNotes(e.target.value), []);
  const handleMasterFolioToggle = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setOpenMasterFolio(e.target.checked), []);
  const handleClearContactGuest = useCallback(() => setContactGuest(null), []);
  const handleCancelForm = useCallback(() => navigate('/reservations/groups'), [navigate]);

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-2">
        <MaterialIcon name="groups" className="text-primary" />
        <h1 className="text-2xl font-display font-bold tracking-tight text-on-surface">
          {t('new_group')}
        </h1>
      </div>

      {error && (
        <div className="p-3 rounded-shape-sm bg-error-container text-on-error-container text-sm" role="alert">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-6">
        <M3Card className="p-6 space-y-4">
          <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider">
            {t('group_details')}
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <M3TextField label={t('group_name')} value={name} onChange={handleNameChange} required />
            <M3TextField label={t('company_name')} value={companyName} onChange={handleCompanyChange} />
            <M3TextField
              label={t('label_checkin_date')} type="date" value={checkInDate}
              onChange={handleCheckInChange} required
            />
            <M3TextField
              label={t('label_checkout_date')} type="date" value={checkOutDate}
              onChange={handleCheckOutChange} required
            />
            <M3TextField
              label={t('group_rate_per_night')} type="number" min="0" step="0.01"
              value={groupRatePerNight} onChange={handleRateChange}
              supportingText={t('group_rate_hint')}
            />
          </div>
          <M3Textarea
            label={t('notes')} value={notes} onChange={handleNotesChange} rows={2}
          />
          <M3Checkbox
            label={t('open_master_folio')}
            supportingText={t('open_master_folio_hint')}
            checked={openMasterFolio}
            onChange={handleMasterFolioToggle}
          />
        </M3Card>

        <M3Card className="p-6 space-y-4">
          <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider">
            {t('contact_guest')}
          </h2>
          <GuestSearchAndCreate
            selectedGuest={contactGuest}
            onSelectGuest={setContactGuest}
            onClearGuest={handleClearContactGuest}
          />
        </M3Card>

        <M3Card className="p-6 space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-sm font-medium text-on-surface-variant uppercase tracking-wider">
              {t('rooming_list')}
            </h2>
            <M3Button type="button" variant="tonal" icon="add" onClick={handleAddRow}>
              {t('add_room')}
            </M3Button>
          </div>
          <div className="space-y-4">
            {rows.map((row) => (
              <RoomingListRow
                key={row.tempId}
                row={row}
                availableRooms={availableRooms}
                roomTakenElsewhere={roomTakenElsewhere}
                openMasterFolio={openMasterFolio}
                onChange={handleRowChange}
                onRemove={handleRemoveRow}
                canRemove={rows.length > 1}
              />
            ))}
          </div>
        </M3Card>

        <div className="flex justify-end gap-2">
          <M3Button type="button" variant="outlined" onClick={handleCancelForm}>
            {t('cancel')}
          </M3Button>
          <M3Button type="submit" disabled={!canSubmit} loading={createGroup.isPending}>
            {t('create_group')}
          </M3Button>
        </div>
      </form>
    </div>
  );
};
