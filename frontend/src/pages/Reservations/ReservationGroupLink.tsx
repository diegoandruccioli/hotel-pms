import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { useReservationGroup } from '../../hooks/queries';

/** Link from a rooming-list reservation back to its group; the name is a nicety, so the link
 * stays usable (generic label) while the group loads or if it cannot be read. */
export const ReservationGroupLink = ({ groupId }: { groupId: string }) => {
  const { t } = useTranslation('common');
  const { data: group } = useReservationGroup(groupId);

  return (
    <Link
      to={`/reservations/groups/${groupId}`}
      className="inline-flex items-center gap-2 min-h-10 text-sm font-body text-primary hover:underline focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary rounded-shape-xs"
    >
      <MaterialIcon name="groups" size={18} />
      {group ? t('reservation_part_of_group', { name: group.name }) : t('reservation_group_link')}
    </Link>
  );
};
