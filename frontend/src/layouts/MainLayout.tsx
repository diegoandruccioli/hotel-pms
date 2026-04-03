import { useState, useCallback, memo } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { ToastContainer } from '../components/Toast';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { UserMenu } from '../components/UserMenu';
import { SettingsModal } from '../components/SettingsModal';
import * as FocusTrapModule from 'focus-trap-react';
const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

const navigation = [
  { nameKey: 'dashboard', href: '/', icon: 'dashboard' },
  { nameKey: 'guests', href: '/guests', icon: 'group' },
  { nameKey: 'reservations', href: '/reservations', icon: 'event' },
  { nameKey: 'calendar', href: '/calendar', icon: 'date_range' },
  { nameKey: 'stays', href: '/stays', icon: 'hotel' },
  { nameKey: 'housekeeping', href: '/housekeeping', icon: 'cleaning_services' },
  { nameKey: 'billing', href: '/billing', icon: 'receipt_long' },
  { nameKey: 'restaurant', href: '/restaurant', icon: 'restaurant' },
  { nameKey: 'rooms', href: '/rooms', icon: 'meeting_room' },
];

const ownerNavigation = [
  { nameKey: 'owner_dashboard', href: '/owner-dashboard', icon: 'bar_chart' },
];

const getRailNavItemClasses = ({ isActive }: { isActive: boolean }) =>
  `group flex flex-col items-center gap-0.5 px-1 py-1.5 rounded-shape-lg transition-colors ${
    isActive
      ? 'text-on-primary-container'
      : 'text-on-surface-variant hover:text-on-surface'
  }`;

const getDrawerNavItemClasses = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-3 px-4 py-3 mx-3 rounded-shape-full text-sm font-medium font-body transition-colors ${
    isActive
      ? 'bg-primary-container text-on-primary-container'
      : 'text-on-surface-variant hover:bg-surface-container-highest hover:text-on-surface'
  }`;

/* ── Navigation Item (Rail — desktop) ───────────────── */

const RailNavItem = memo(({
  item,
  label,
  end,
}: {
  item: { nameKey: string; href: string; icon: string };
  label: string;
  end?: boolean;
}) => (
  <NavLink
    to={item.href}
    end={end}
    className={getRailNavItemClasses}
  >
    {({ isActive }) => (
      <>
        <span
          className={`flex items-center justify-center w-14 h-8 rounded-shape-full transition-colors ${
            isActive
              ? 'bg-primary-container'
              : 'group-hover:bg-surface-container-highest'
          }`}
        >
          <MaterialIcon name={item.icon} filled={isActive} size={24} />
        </span>
        <span className="text-[11px] font-medium font-body leading-tight text-center truncate w-full">
          {label}
        </span>
      </>
    )}
  </NavLink>
));

RailNavItem.displayName = 'RailNavItem';

/* ── Navigation Item (Drawer — mobile) ──────────────── */

const DrawerNavItem = memo(({
  item,
  label,
  end,
  onClose,
}: {
  item: { nameKey: string; href: string; icon: string };
  label: string;
  end?: boolean;
  onClose: () => void;
}) => (
  <NavLink
    to={item.href}
    end={end}
    onClick={onClose}
    className={getDrawerNavItemClasses}
  >
    {({ isActive }) => (
      <>
        <MaterialIcon name={item.icon} filled={isActive} size={24} />
        {label}
      </>
    )}
  </NavLink>
));

DrawerNavItem.displayName = 'DrawerNavItem';

/* ── Main Layout Component ──────────────────────────── */

export const MainLayout = () => {
  const { t } = useTranslation('common');
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const [drawerOpen, setDrawerOpen]     = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  const handleLogout   = useCallback(() => { logout(); navigate('/login'); }, [logout, navigate]);
  const openDrawer     = useCallback(() => setDrawerOpen(true), []);
  const closeDrawer    = useCallback(() => setDrawerOpen(false), []);
  const toggleUserMenu = useCallback(() => setUserMenuOpen((v) => !v), []);
  const closeUserMenu  = useCallback(() => setUserMenuOpen(false), []);
  const openSettings   = useCallback(() => setSettingsOpen(true), []);
  const closeSettings  = useCallback(() => setSettingsOpen(false), []);

  const isOwnerOrAdmin = user?.role === 'OWNER' || user?.role === 'ADMIN';
  const username       = user?.username ?? t('guest');
  const roleLabel      = user?.role ? t(`role_${user.role.toLowerCase()}`) : t('role_guest');

  return (
    <div className="h-full flex overflow-hidden bg-surface">
      {/* ── Mobile Modal Drawer ─────────────────────── */}
      {drawerOpen && (
        <FocusTrap>
          <div className="fixed inset-0 z-50 flex md:hidden" role="dialog" aria-modal="true">
            {/* Scrim */}
            <div
              className="fixed inset-0 bg-scrim/40 transition-opacity"
              onClick={closeDrawer}
              aria-hidden="true"
            />

            {/* Drawer Panel */}
            <nav className="relative flex flex-col w-72 max-w-[85vw] h-full bg-surface rounded-r-shape-lg shadow-elevation-3 animate-slide-in-right overflow-y-auto">
              <div className="flex items-center gap-3 px-4 pt-5 pb-3">
                <div className="flex items-center justify-center w-10 h-10 bg-primary-container rounded-shape-lg">
                  <MaterialIcon name="apartment" size={24} className="text-on-primary-container" />
                </div>
                <span className="text-lg font-display font-bold text-on-surface">Hotel PMS</span>
              </div>

              <div className="flex-1 pt-2 pb-4 space-y-0.5">
                {navigation.map((item) => (
                  <DrawerNavItem
                    key={item.nameKey}
                    item={item}
                    label={t(`nav_${item.nameKey}`)}
                    end={item.href === '/'}
                    onClose={closeDrawer}
                  />
                ))}

                {isOwnerOrAdmin && (
                  <>
                    <div className="my-2 mx-4 border-t border-outline-variant" />
                    {ownerNavigation.map((item) => (
                      <DrawerNavItem
                        key={item.nameKey}
                        item={item}
                        label={t(`nav_${item.nameKey}`)}
                        onClose={closeDrawer}
                      />
                    ))}
                  </>
                )}
              </div>
            </nav>
          </div>
        </FocusTrap>
      )}

      {/* ── Desktop Navigation Rail ─────────────────── */}
      <aside className="hidden md:flex flex-col items-center w-20 flex-shrink-0 glass-surface border-r border-outline-variant/50 py-4 gap-1 overflow-y-auto">
        <div className="flex items-center justify-center w-14 h-14 mb-3 bg-primary-container rounded-shape-lg">
          <MaterialIcon name="apartment" size={28} className="text-on-primary-container" />
        </div>

        <nav className="flex flex-col items-center gap-0.5 flex-1">
          {navigation.map((item) => (
            <RailNavItem
              key={item.nameKey}
              item={item}
              label={t(`nav_${item.nameKey}`)}
              end={item.href === '/'}
            />
          ))}

          {isOwnerOrAdmin && (
            <>
              <div className="w-8 my-1.5 border-t border-outline-variant" />
              {ownerNavigation.map((item) => (
                <RailNavItem
                  key={item.nameKey}
                  item={item}
                  label={t(`nav_${item.nameKey}`)}
                />
              ))}
            </>
          )}
        </nav>
      </aside>

      {/* ── Main content area ───────────────────────── */}
      <div className="flex flex-col flex-1 w-0 overflow-hidden">
        {/* Top Bar */}
        <header className="relative z-10 flex-shrink-0 flex items-center h-16 glass-surface-elevated border-b border-outline-variant/30 shadow-elevation-1 px-4">
          {/* Mobile hamburger */}
          <button
            type="button"
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest md:hidden mr-2"
            onClick={openDrawer}
            aria-label={t('nav_menu') ?? 'Open menu'}
          >
            <MaterialIcon name="menu" size={24} />
          </button>

          <div className="flex-1" />

          {/* Username + role (desktop only) */}
          <div className="hidden sm:flex flex-col items-end mr-2">
            <span className="text-sm font-medium font-body text-on-surface">{username}</span>
            <span className="text-xs font-body text-on-surface-variant capitalize">{roleLabel}</span>
          </div>

          {/* Avatar + dropdown */}
          <UserMenu
            username={username}
            roleLabel={roleLabel}
            open={userMenuOpen}
            onToggle={toggleUserMenu}
            onClose={closeUserMenu}
            onOpenSettings={openSettings}
            onLogout={handleLogout}
          />
        </header>

        {/* Page content */}
        <main className="flex-1 relative overflow-y-auto focus:outline-none">
          <div className="py-6">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 md:px-8">
              <Outlet />
            </div>
          </div>
        </main>
      </div>

      {/* Settings Modal */}
      <SettingsModal open={settingsOpen} onClose={closeSettings} />

      {/* Global Toast Notifications */}
      <ToastContainer />
    </div>
  );
};
