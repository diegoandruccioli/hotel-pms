import { memo, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useFormatters } from '../../hooks';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card, M3StatusChip, M3Table, M3TableActionLink, M3TableCell, M3TableRow } from '../../components/m3';
import { reservationStatusTone } from '../../utils';
import type { GroupMemberResponse, ReservationStatus, RoomResponse } from '../../types';
import { summarizeRoomingList } from './roomingListReadiness';

interface RoomingListCardProps {
  members: GroupMemberResponse[];
  /** Room lookup; undefined while loading or when it failed — rooms then show as a dash. */
  rooms: RoomResponse[] | undefined;
}

interface MemberRowProps {
  member: GroupMemberResponse;
  room: RoomResponse | undefined;
}

const MemberRow = memo(({ member, room }: MemberRowProps) => {
  const { t } = useTranslation('common');
  const { formatCurrency } = useFormatters();
  const navigate = useNavigate();

  const handleCheckIn = useCallback(() => {
    navigate(`/stays/check-in/${member.reservationId}`, {
      state: { roomId: member.roomId ?? '', expectedGuests: member.expectedGuests, guestId: member.guestId },
    });
  }, [navigate, member]);
  const handleView = useCallback(() => navigate(`/reservations/${member.reservationId}`), [navigate, member.reservationId]);
  const handleEdit = useCallback(() => navigate(`/reservations/edit/${member.reservationId}`), [navigate, member.reservationId]);

  return (
    <M3TableRow>
      <M3TableCell>
        <span className="font-medium">{room?.roomNumber ?? '—'}</span>
        {room?.roomType?.name && <span className="block text-xs text-on-surface-variant">{room.roomType.name}</span>}
      </M3TableCell>
      <M3TableCell>
        {member.guestFullName}
        <span className="flex items-center gap-1 text-xs text-on-surface-variant">
          <MaterialIcon name="group" size={14} />
          <span className="sr-only">{t('label_expected_guests')}: </span>
          {member.expectedGuests}
        </span>
      </M3TableCell>
      <M3TableCell>
        <div className="flex flex-wrap gap-1">
          <M3StatusChip
            label={t(`status_${member.status.toLowerCase()}`, member.status)}
            tone={reservationStatusTone[member.status as ReservationStatus] ?? 'neutral'}
          />
          {member.roomId === null && <M3StatusChip label={t('rooming_unassigned')} tone="neutral" />}
        </div>
      </M3TableCell>
      <M3TableCell>
        {member.billedToMasterFolio ? <MaterialIcon name="check" size={16} className="text-primary" /> : '—'}
      </M3TableCell>
      <M3TableCell>{formatCurrency(member.price)}</M3TableCell>
      <M3TableCell>
        <div className="flex justify-end gap-x-3 whitespace-nowrap">
          {member.status === 'CONFIRMED' && member.roomId !== null && <M3TableActionLink onClick={handleCheckIn}>{t('check_in')}</M3TableActionLink>}
          <M3TableActionLink onClick={handleView}>{t('view')}</M3TableActionLink>
          <M3TableActionLink onClick={handleEdit}>{t('edit')}</M3TableActionLink>
        </div>
      </M3TableCell>
    </M3TableRow>
  );
});
MemberRow.displayName = 'MemberRow';

/** Rooming list of a group: room, guest, status per member and how many rooms are ready. */
export const RoomingListCard = memo(({ members, rooms }: RoomingListCardProps) => {
  const { t } = useTranslation('common');
  const roomsById = useMemo(() => new Map((rooms ?? []).map((r) => [r.id, r])), [rooms]);
  const { ready, total } = useMemo(() => summarizeRoomingList(members), [members]);
  const headers = useMemo(
    () => [t('label_room'), t('guest'), t('status'),
      t('billed_to_master_folio'), t('amount'), t('actions')],
    [t],
  );
  const allReady = total > 0 && ready === total;
  const progressLabel = t('rooming_progress', { count: total, ready, total });
  const barStyle = useMemo(() => ({ width: `${total === 0 ? 0 : (ready / total) * 100}%` }), [ready, total]);

  return (
    <M3Card variant="solid" className="p-6 space-y-4">
      <section aria-labelledby="rooming-list-title" className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="rooming-list-title" className="text-xl font-display font-medium text-on-surface">{t('rooming_list')}</h2>
          <M3StatusChip
            label={progressLabel}
            tone={allReady ? 'success' : 'warning'}
          />
        </div>
        {total > 0 && (
          <div
            role="progressbar"
            aria-label={t('rooming_progress_label')}
            aria-valuemin={0}
            aria-valuemax={total}
            aria-valuenow={ready}
            aria-valuetext={progressLabel}
            className="h-2 w-full overflow-hidden rounded-full bg-surface-variant"
          >
            <div className={allReady ? 'h-full bg-tertiary' : 'h-full bg-secondary'} style={barStyle} />
          </div>
        )}
        <M3Table headers={headers}>
          {members.map((member) => (
            <MemberRow key={member.reservationId} member={member} room={member.roomId ? roomsById.get(member.roomId) : undefined} />
          ))}
        </M3Table>
      </section>
    </M3Card>
  );
});
RoomingListCard.displayName = 'RoomingListCard';
