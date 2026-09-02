import { useCallback, useEffect, useMemo, useState } from 'react';
import { Command } from 'cmdk';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from './MaterialIcon';
import { useAuthStore } from '../store';
import { useCommandPaletteGuests, useCommandPaletteReservations } from '../hooks/queries/useCommandPaletteSearch';
import type { Role } from '../types';
import type { GuestResponseDTO } from '../types';
import type { ReservationResponse } from '../types';

const OWNER_ADMIN_ROLES = ['OWNER', 'ADMIN'] as const;
const SEARCH_DEBOUNCE_MS = 300;

interface NavItem {
  id: string;
  href: string;
  icon: string;
  labelKey: string;
  ns: 'common' | 'settings';
  allowedRoles?: readonly Role[];
}

/** Every static, directly-navigable route in the app (same set App.tsx
 * routes and MainLayout's sidebar + Settings hub cover), collected in one
 * place so the palette doesn't drift from either as routes change. */
const NAV_ITEMS: NavItem[] = [
  { id: 'dashboard', href: '/', icon: 'dashboard', labelKey: 'nav_dashboard', ns: 'common' },
  { id: 'guests', href: '/guests', icon: 'group', labelKey: 'nav_guests', ns: 'common' },
  { id: 'reservations', href: '/reservations', icon: 'event', labelKey: 'nav_reservations', ns: 'common' },
  { id: 'quotations', href: '/quotations', icon: 'request_quote', labelKey: 'nav_quotations', ns: 'common' },
  { id: 'calendar', href: '/calendar', icon: 'date_range', labelKey: 'nav_calendar', ns: 'common' },
  { id: 'stays', href: '/stays', icon: 'hotel', labelKey: 'nav_stays', ns: 'common' },
  { id: 'housekeeping', href: '/housekeeping', icon: 'cleaning_services', labelKey: 'nav_housekeeping', ns: 'common' },
  { id: 'billing', href: '/billing', icon: 'receipt_long', labelKey: 'nav_billing', ns: 'common' },
  { id: 'restaurant', href: '/restaurant', icon: 'restaurant', labelKey: 'nav_restaurant', ns: 'common' },
  { id: 'rooms', href: '/rooms', icon: 'meeting_room', labelKey: 'nav_rooms', ns: 'common' },
  { id: 'rates', href: '/rates', icon: 'payments', labelKey: 'nav_rates', ns: 'common' },
  { id: 'owner-dashboard', href: '/owner-dashboard', icon: 'bar_chart', labelKey: 'nav_owner_dashboard', ns: 'common', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings', href: '/settings', icon: 'settings', labelKey: 'settings', ns: 'settings' },
  { id: 'settings-profile', href: '/settings/profile', icon: 'person', labelKey: 'my_profile', ns: 'settings' },
  { id: 'settings-password', href: '/settings/password', icon: 'lock', labelKey: 'change_password', ns: 'settings' },
  { id: 'settings-accessibility', href: '/settings/accessibility', icon: 'accessibility_new', labelKey: 'settings_section_accessibility', ns: 'settings' },
  { id: 'settings-appearance', href: '/settings/appearance', icon: 'palette', labelKey: 'settings_appearance_language_title', ns: 'settings' },
  { id: 'settings-system', href: '/settings/system', icon: 'admin_panel_settings', labelKey: 'settings_section_system', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'hotel-profile', href: '/profile/hotel', icon: 'apartment', labelKey: 'settings_section_hotel_profile', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'admin-users', href: '/admin/users', icon: 'manage_accounts', labelKey: 'settings_section_admin_users', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
  { id: 'settings-city-tax', href: '/settings/city-tax', icon: 'account_balance', labelKey: 'settings_section_city_tax', ns: 'settings', allowedRoles: OWNER_ADMIN_ROLES },
];

const ITEM_CLASS =
  'flex items-center gap-3 px-4 py-3 rounded-shape-sm text-sm font-body text-on-surface cursor-pointer aria-selected:bg-primary-container aria-selected:text-on-primary-container';

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export const CommandPalette = ({ open, onClose }: CommandPaletteProps) => {
  const { t } = useTranslation('command');
  const { t: tCommon } = useTranslation('common');
  const { t: tSettings } = useTranslation('settings');
  const navigate = useNavigate();
  const role = useAuthStore((s) => s.user?.role);

  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(search), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [search]);

  // Reset on close so the next open starts from a blank slate instead of
  // whatever was last typed. Adjusted during render (not an effect) per
  // the codebase's established prevProp-comparison idiom.
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (!open) {
      setSearch('');
      setDebouncedSearch('');
    }
  }

  const { data: guestResults } = useCommandPaletteGuests(debouncedSearch);
  const { data: reservationResults } = useCommandPaletteReservations(debouncedSearch);

  const visibleNavItems = useMemo(
    () => NAV_ITEMS.filter((item) => !item.allowedRoles || (role && item.allowedRoles.includes(role))),
    [role],
  );

  const goTo = useCallback((href: string, state?: Record<string, unknown>) => {
    navigate(href, state ? { state } : undefined);
    onClose();
  }, [navigate, onClose]);

  const handleNewReservation = useCallback(() => goTo('/reservations/new'), [goTo]);
  const handleWalkIn = useCallback(() => goTo('/stays/walk-in'), [goTo]);

  const handleGuestSelect = useCallback((guest: GuestResponseDTO) => {
    goTo(`/guests?search=${encodeURIComponent(`${guest.firstName} ${guest.lastName}`)}`);
  }, [goTo]);

  const handleReservationSelect = useCallback((reservation: ReservationResponse) => {
    goTo(`/reservations/${reservation.id}`);
  }, [goTo]);

  const handleOpenChange = useCallback((next: boolean) => {
    if (!next) onClose();
  }, [onClose]);

  return (
    <Command.Dialog
      open={open}
      onOpenChange={handleOpenChange}
      label={t('palette_label')}
      overlayClassName="fixed inset-0 z-50 bg-scrim/40"
      contentClassName="fixed left-1/2 top-24 z-50 w-full max-w-lg -translate-x-1/2 overflow-hidden rounded-shape-xl bg-surface-container-high shadow-elevation-3"
    >
      <div className="flex items-center gap-3 border-b border-outline-variant px-4">
        <MaterialIcon name="search" size={20} className="text-on-surface-variant" />
        <Command.Input
          value={search}
          onValueChange={setSearch}
          placeholder={t('palette_placeholder')}
          className="h-14 flex-1 bg-transparent text-sm font-body text-on-surface placeholder-on-surface-variant focus:outline-hidden focus:ring-2 focus:ring-primary focus:ring-inset"
        />
      </div>

      <Command.List className="max-h-[60vh] overflow-y-auto p-2">
        <Command.Empty className="px-4 py-6 text-center text-sm font-body text-on-surface-variant">
          {t('palette_empty')}
        </Command.Empty>

        <Command.Group heading={t('palette_group_actions')} className="**:[[cmdk-group-heading]]:px-4 **:[[cmdk-group-heading]]:py-2 **:[[cmdk-group-heading]]:text-xs **:[[cmdk-group-heading]]:font-medium **:[[cmdk-group-heading]]:text-on-surface-variant">
          <Command.Item value="new-reservation" onSelect={handleNewReservation} className={ITEM_CLASS}>
            <MaterialIcon name="add_circle" size={18} className="text-primary" />
            {t('palette_action_new_reservation')}
          </Command.Item>
          <Command.Item value="walk-in" onSelect={handleWalkIn} className={ITEM_CLASS}>
            <MaterialIcon name="person_add" size={18} className="text-primary" />
            {t('palette_action_new_walkin')}
          </Command.Item>
        </Command.Group>

        <Command.Group heading={t('palette_group_navigate')} className="**:[[cmdk-group-heading]]:px-4 **:[[cmdk-group-heading]]:py-2 **:[[cmdk-group-heading]]:text-xs **:[[cmdk-group-heading]]:font-medium **:[[cmdk-group-heading]]:text-on-surface-variant">
          {visibleNavItems.map((item) => (
            <NavCommandItem key={item.id} item={item} onSelect={goTo} tCommon={tCommon} tSettings={tSettings} />
          ))}
        </Command.Group>

        {guestResults && guestResults.content.length > 0 && (
          <Command.Group heading={t('palette_group_guests')} className="**:[[cmdk-group-heading]]:px-4 **:[[cmdk-group-heading]]:py-2 **:[[cmdk-group-heading]]:text-xs **:[[cmdk-group-heading]]:font-medium **:[[cmdk-group-heading]]:text-on-surface-variant">
            {guestResults.content.map((guest) => (
              <GuestCommandItem key={guest.id} guest={guest} onSelect={handleGuestSelect} />
            ))}
          </Command.Group>
        )}

        {reservationResults && reservationResults.content.length > 0 && (
          <Command.Group heading={t('palette_group_reservations')} className="**:[[cmdk-group-heading]]:px-4 **:[[cmdk-group-heading]]:py-2 **:[[cmdk-group-heading]]:text-xs **:[[cmdk-group-heading]]:font-medium **:[[cmdk-group-heading]]:text-on-surface-variant">
            {reservationResults.content.map((reservation) => (
              <ReservationCommandItem key={reservation.id} reservation={reservation} onSelect={handleReservationSelect} t={t} />
            ))}
          </Command.Group>
        )}
      </Command.List>

      <div className="flex items-center gap-4 border-t border-outline-variant px-4 py-2 text-xs font-body text-on-surface-variant">
        <span><kbd className="rounded-sm border border-outline-variant px-1">↑↓</kbd> {t('palette_hint_navigate')}</span>
        <span><kbd className="rounded-sm border border-outline-variant px-1">↵</kbd> {t('palette_hint_select')}</span>
        <span><kbd className="rounded-sm border border-outline-variant px-1">esc</kbd> {t('palette_hint_close')}</span>
      </div>
    </Command.Dialog>
  );
};

const NavCommandItem = ({ item, onSelect, tCommon, tSettings }: {
  item: NavItem;
  onSelect: (href: string) => void;
  tCommon: (key: string) => string;
  tSettings: (key: string) => string;
}) => {
  const label = item.ns === 'common' ? tCommon(item.labelKey) : tSettings(item.labelKey);
  const handleSelect = useCallback(() => onSelect(item.href), [onSelect, item.href]);
  const keywords = useMemo(() => [label], [label]);
  return (
    <Command.Item value={`nav-${item.id}-${label}`} onSelect={handleSelect} className={ITEM_CLASS} keywords={keywords}>
      <MaterialIcon name={item.icon} size={18} className="text-on-surface-variant" />
      {label}
    </Command.Item>
  );
};

const GuestCommandItem = ({ guest, onSelect }: { guest: GuestResponseDTO; onSelect: (g: GuestResponseDTO) => void }) => {
  const handleSelect = useCallback(() => onSelect(guest), [onSelect, guest]);
  const fullName = `${guest.firstName} ${guest.lastName}`;
  const keywords = useMemo(() => [fullName, guest.email], [fullName, guest.email]);
  return (
    <Command.Item value={`guest-${guest.id}`} onSelect={handleSelect} className={ITEM_CLASS} keywords={keywords}>
      <MaterialIcon name="person" size={18} className="text-on-surface-variant" />
      <span className="flex-1 truncate">{fullName}</span>
      <span className="text-xs text-on-surface-variant truncate">{guest.email}</span>
    </Command.Item>
  );
};

const ReservationCommandItem = ({ reservation, onSelect, t }: {
  reservation: ReservationResponse;
  onSelect: (r: ReservationResponse) => void;
  t: (key: string, opts?: Record<string, unknown>) => string;
}) => {
  const handleSelect = useCallback(() => onSelect(reservation), [onSelect, reservation]);
  const label = t('palette_reservation_result', {
    guest: reservation.guestFullName,
    checkIn: reservation.checkInDate,
  });
  const keywords = useMemo(() => [reservation.guestFullName ?? ''], [reservation.guestFullName]);
  return (
    <Command.Item value={`reservation-${reservation.id}`} onSelect={handleSelect} className={ITEM_CLASS} keywords={keywords}>
      <MaterialIcon name="event" size={18} className="text-on-surface-variant" />
      {label}
    </Command.Item>
  );
};
