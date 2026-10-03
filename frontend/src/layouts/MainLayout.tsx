import { useState, useCallback, useEffect, useMemo } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store';
import { authService } from '../services';
import { ToastContainer } from '../components/Toast';
import { useTranslation } from 'react-i18next';
import { MaterialIcon } from '../components/MaterialIcon';
import { UserMenu } from '../components/UserMenu';
import { RouteAnnouncer } from '../components/RouteAnnouncer';
import { CommandPalette } from '../components/CommandPalette';
import { useEscapeKey } from '../hooks';
import { useServerEvents } from '../hooks';
import * as FocusTrapModule from 'focus-trap-react';
import { SidebarNav } from '../components/SidebarNav';
import { getSidebarSections } from '../config/navigation';
const FocusTrap = FocusTrapModule.default ?? FocusTrapModule;

/* ── Main Layout Component ──────────────────────────── */

export const MainLayout = () => {
  const { t } = useTranslation('common');
  const { t: tCommand } = useTranslation('command');
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const [drawerOpen, setDrawerOpen]     = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const [paletteOpen, setPaletteOpen]   = useState(false);

  // Must call the backend before clearing local state: this is what actually
  // blacklists the refresh token server-side (AuthController.logout ->
  // RefreshTokenService.blacklist). Without it, clicking "Logout" only wiped
  // the Zustand store — the httpOnly jwt/refresh_token cookies stayed fully
  // valid (access token up to 15 min, refresh token up to 7 days), so a
  // direct navigation or API call after "logout" kept working as the same
  // user. The local state clear + redirect still happen even if the network
  // call fails, so the user can always leave the UI locally.
  const handleLogout = useCallback(() => {
    void authService.logout().catch(() => {});
    logout();
    navigate('/login');
  }, [logout, navigate]);
  const openDrawer     = useCallback(() => setDrawerOpen(true), []);
  const closeDrawer    = useCallback(() => setDrawerOpen(false), []);
  const toggleUserMenu = useCallback(() => setUserMenuOpen((v) => !v), []);
  const closeUserMenu  = useCallback(() => setUserMenuOpen(false), []);
  const openSettings   = useCallback(() => navigate('/settings'), [navigate]);
  const openPalette    = useCallback(() => setPaletteOpen(true), []);
  const closePalette   = useCallback(() => setPaletteOpen(false), []);

  useEscapeKey(drawerOpen, closeDrawer);

  // The drawer is `lg:hidden`: if the viewport grows past the breakpoint while
  // it is open, its focus trap would be left holding a hidden dialog.
  useEffect(() => {
    if (!drawerOpen) return undefined;
    const query = window.matchMedia('(min-width: 1024px)');
    const closeOnDesktop = (e: MediaQueryListEvent) => {
      if (e.matches) setDrawerOpen(false);
    };
    query.addEventListener('change', closeOnDesktop);
    return () => query.removeEventListener('change', closeOnDesktop);
  }, [drawerOpen]);
  useServerEvents();

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen((v) => !v);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const sidebarSections = useMemo(() => getSidebarSections(user?.role), [user?.role]);
  const username       = user?.username ?? t('guest');
  const roleLabel      = user?.role ? t(`role_${user.role.toLowerCase()}`) : t('role_guest');

  return (
    <div className="h-full flex overflow-hidden bg-surface-container-low">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:rounded-shape-full focus:bg-primary focus:text-on-primary focus:ring-2 focus:ring-primary focus:ring-offset-2"
      >
        {t('skip_to_main')}
      </a>
      <RouteAnnouncer />
      {/* ── Mobile Modal Drawer ─────────────────────── */}
      {drawerOpen && (
        <FocusTrap>
          <div className="fixed inset-0 z-50 flex lg:hidden" role="dialog" aria-modal="true" aria-label={t('nav_menu')}>
            {/* Scrim */}
            <div
              className="fixed inset-0 bg-scrim/40 transition-opacity"
              onClick={closeDrawer}
              aria-hidden="true"
            />

            {/* Drawer Panel */}
            <div className="relative flex flex-col w-72 max-w-[85vw] h-full bg-surface-container-lowest rounded-r-shape-lg shadow-elevation-3 animate-slide-in-right overflow-y-auto">
              <div className="flex items-center gap-3 px-4 pt-5 pb-3">
                <div className="flex items-center justify-center w-10 h-10 bg-primary-container rounded-shape-lg">
                  <MaterialIcon name="apartment" size={24} className="text-on-primary-container" />
                </div>
                <span className="text-lg font-display font-bold text-on-surface">Hotel PMS</span>
              </div>
              <SidebarNav sections={sidebarSections} onNavigate={closeDrawer} ariaLabel={t('nav_drawer')} />
            </div>
          </div>
        </FocusTrap>
      )}

      {/* ── Desktop Sidebar ─────────────────────────── */}
      <aside className="hidden lg:flex flex-col fixed left-0 top-0 h-full w-66 bg-surface-container-lowest border-r border-outline-variant overflow-y-auto scrollbar-gutter-stable z-20">
        <div className="flex items-center gap-3 px-6 pt-5 pb-4">
          <div className="flex items-center justify-center w-10 h-10 bg-primary-container rounded-shape-lg">
            <MaterialIcon name="apartment" size={24} className="text-on-primary-container" />
          </div>
          <span className="text-lg font-display font-bold text-on-surface">Hotel PMS</span>
        </div>
        <SidebarNav sections={sidebarSections} />
      </aside>

      {/* ── Main content area ───────────────────────── */}
      <div className="flex flex-col flex-1 w-0 overflow-hidden lg:ml-66">
        {/* Top Bar */}
        <header className="relative z-10 shrink-0 flex items-center h-16 bg-surface-container-lowest border-b border-outline-variant px-4">
          {/* Mobile hamburger */}
          <button
            type="button"
            className="flex items-center justify-center w-10 h-10 rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest lg:hidden mr-2"
            onClick={openDrawer}
            aria-label={t('nav_menu') ?? 'Open menu'}
          >
            <MaterialIcon name="menu" size={24} />
          </button>

          <div className="flex-1" />

          {/* Command palette trigger */}
          <button
            type="button"
            onClick={openPalette}
            aria-label={tCommand('palette_open_button')}
            className="flex items-center gap-2 h-10 px-3 mr-2 rounded-shape-full text-on-surface-variant hover:bg-surface-container-highest focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
          >
            <MaterialIcon name="search" size={20} />
            <kbd className="hidden sm:inline text-xs rounded-sm border border-outline-variant px-1.5 py-0.5">
              {navigator.platform.toUpperCase().includes('MAC') ? '⌘K' : 'Ctrl K'}
            </kbd>
          </button>

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
        <main id="main-content" className="flex-1 relative overflow-y-auto focus:outline-hidden" tabIndex={-1}>
          <div className="py-6">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 md:px-8">
              <Outlet />
            </div>
          </div>
        </main>
      </div>

      {/* Global Toast Notifications */}
      <ToastContainer />

      <CommandPalette open={paletteOpen} onClose={closePalette} />
    </div>
  );
};
