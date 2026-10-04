import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../../components/MaterialIcon';
import { M3Card } from '../../components/m3';
import { NIGHT_AUDIT_ROLES } from '../../config/navigation';
import type { DaySheetResponse, Role } from '../../types';

interface TaskItem {
  key: string;
  icon: string;
  title: string;
  subtitle: string;
  to: string;
  state?: Record<string, unknown>;
}

interface TodayTasksCardProps {
  daySheet: DaySheetResponse;
  role: Role | undefined;
}

/**
 * "To do today": links built only from numbers the dashboard already has
 * (day-sheet counts and the role), so it adds no request. An item appears only
 * when there is something to do; night audit is always offered to the roles that
 * may run it.
 */
export const TodayTasksCard = ({ daySheet, role }: TodayTasksCardProps) => {
  const { t } = useTranslation('common');

  const items = useMemo<TaskItem[]>(() => {
    const dirty = daySheet.roomStatusCounts.DIRTY ?? 0;
    const outOfService = daySheet.roomStatusCounts.MAINTENANCE ?? 0;
    const list: TaskItem[] = [];
    if (dirty > 0) {
      list.push({
        key: 'dirty', icon: 'cleaning_services', to: '/housekeeping',
        title: t('dashboard_task_dirty_rooms', { count: dirty }),
        subtitle: t('dashboard_task_dirty_rooms_sub'),
      });
    }
    if (outOfService > 0) {
      list.push({
        key: 'maintenance', icon: 'build', to: '/housekeeping',
        title: t('dashboard_task_out_of_service', { count: outOfService }),
        subtitle: t('dashboard_task_out_of_service_sub'),
      });
    }
    if (daySheet.todayDepartures > 0) {
      list.push({
        key: 'departures', icon: 'logout', to: '/stays',
        state: { statusFilter: 'CHECKED_IN', sortField: 'expectedCheckOutDate', sortDir: 'asc' },
        title: t('dashboard_task_departures', { count: daySheet.todayDepartures }),
        subtitle: t('dashboard_task_departures_sub'),
      });
    }
    if (role && NIGHT_AUDIT_ROLES.includes(role)) {
      list.push({
        key: 'night-audit', icon: 'fact_check', to: '/night-audit',
        title: t('dashboard_task_night_audit'),
        subtitle: t('dashboard_task_night_audit_sub'),
      });
    }
    return list;
  }, [daySheet, role, t]);

  if (items.length === 0) return null;

  return (
    <M3Card variant="solid" className="p-5" data-testid="today-tasks">
      <div className="flex items-center gap-2 mb-2">
        <MaterialIcon name="checklist" size={20} className="text-primary" />
        <h2 className="text-sm font-display font-semibold text-on-surface">{t('dashboard_tasks_title')}</h2>
      </div>
      <ul className="divide-y divide-outline-variant">
        {items.map((item) => (
          <li key={item.key}>
            <Link
              to={item.to}
              state={item.state}
              className="flex items-center gap-3 min-h-11 py-2.5 rounded-sm hover:bg-surface-container-low focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary"
            >
              <MaterialIcon name={item.icon} size={20} className="text-on-surface-variant shrink-0" />
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-body font-medium text-on-surface">{item.title}</span>
                <span className="block text-xs font-body text-on-surface-variant">{item.subtitle}</span>
              </span>
              <MaterialIcon name="chevron_right" size={20} className="text-on-surface-variant shrink-0" />
            </Link>
          </li>
        ))}
      </ul>
    </M3Card>
  );
};
