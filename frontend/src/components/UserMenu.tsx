import { useCallback, useEffect, useRef, memo } from 'react';
import { useComponentTranslation } from '../hooks/useComponentTranslation';
import { itUserMenuTranslations } from './UserMenu.it.i18n';
import { enUserMenuTranslations } from './UserMenu.en.i18n';
import { MaterialIcon } from './MaterialIcon';

interface UserMenuProps {
  /** Display name for the avatar initial */
  username: string;
  /** Role label shown under the username */
  roleLabel: string;
  /** Whether the dropdown is currently open */
  open: boolean;
  /** Toggle or close the dropdown */
  onToggle: () => void;
  onClose: () => void;
  onOpenSettings: () => void;
  onLogout: () => void;
}

/** ── Sub-component for individual Menu Items ─────────── */
const UserMenuItem = memo(({
  label,
  icon,
  onClick,
  isError = false,
}: {
  label: string;
  icon: string;
  onClick: () => void;
  isError?: boolean;
}) => (
  <button
    type="button"
    role="menuitem"
    onClick={onClick}
    className={[
      'flex items-center gap-3 w-full px-4 py-3',
      'text-sm font-body text-left transition-colors focus-visible:outline-none',
      isError
        ? 'text-error hover:bg-error-container/20 focus-visible:bg-error-container/20'
        : 'text-on-surface hover:bg-surface-container-highest focus-visible:bg-surface-container-highest',
    ].join(' ')}
  >
    <MaterialIcon
      name={icon}
      size={20}
      className={isError ? 'text-error' : 'text-on-surface-variant'}
    />
    {label}
  </button>
));
UserMenuItem.displayName = 'UserMenuItem';

/**
 * Avatar button that reveals an M3 "Menu" surface with:
 *  - Keyboard: Enter/Space opens, Escape closes, Tab/ArrowDown navigate items
 *  - aria-haspopup="menu" + aria-expanded wired to open state
 *  - Click outside closes the menu
 */
export const UserMenu = ({
  username,
  roleLabel,
  open,
  onToggle,
  onClose,
  onOpenSettings,
  onLogout,
}: UserMenuProps) => {
  const { t } = useComponentTranslation('UserMenu', itUserMenuTranslations, enUserMenuTranslations);
  const menuRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const userInitial = username.charAt(0).toUpperCase();

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: PointerEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [open, onClose]);

  // Close on Escape, while open
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  // Stable handlers for sub-components
  const handleSettingsClick = useCallback(() => {
    onClose();
    onOpenSettings();
  }, [onClose, onOpenSettings]);

  const handleLogoutClick = useCallback(() => {
    onClose();
    onLogout();
  }, [onClose, onLogout]);

  // Arrow-key navigation inside menu items
  const handleMenuKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>) => {
      if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return;
      e.preventDefault();
      const items = Array.from<HTMLElement>(
        menuRef.current?.querySelectorAll('[role="menuitem"]') ?? []
      );
      if (items.length === 0) return;
      const current = document.activeElement as HTMLElement;
      const idx = items.indexOf(current);
      let next = 0;
      if (e.key === 'ArrowDown') next = (idx + 1) % items.length;
      else if (e.key === 'ArrowUp') next = (idx - 1 + items.length) % items.length;
      else if (e.key === 'End') next = items.length - 1;
      items[next]?.focus();
    },
    []
  );

  return (
    <div ref={menuRef} className="relative">
      {/* ── Trigger: Avatar Button ─────────────────── */}
      <button
        ref={triggerRef}
        type="button"
        id="user-menu-trigger"
        onClick={onToggle}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls="user-menu-list"
        className={[
          'flex items-center justify-center w-9 h-9',
          'rounded-shape-full bg-primary text-on-primary',
          'text-sm font-display font-bold',
          'focus-visible:outline-none focus-visible:ring-2',
          'focus-visible:ring-primary focus-visible:ring-offset-2',
          'hover:brightness-110 transition-all cursor-pointer',
          open ? 'ring-2 ring-primary ring-offset-2' : '',
        ].join(' ')}
        aria-label={t('user_menu_label', { name: username })}
      >
        {userInitial}
      </button>

      {/* ── Dropdown Menu Surface ──────────────────── */}
      {open && (
        <div
          id="user-menu-list"
          role="menu"
          tabIndex={-1}
          aria-labelledby="user-menu-trigger"
          onKeyDown={handleMenuKeyDown}
          className={[
            'absolute right-0 top-full mt-2 w-56 z-40',
            'bg-surface-container rounded-[16px]',
            'shadow-elevation-3 border border-outline-variant/30',
            'py-2 overflow-hidden',
            'animate-fade-in',
          ].join(' ')}
        >
          {/* User info header (non-interactive) */}
          <div className="px-4 py-3 border-b border-outline-variant/30">
            <p className="text-sm font-medium text-on-surface truncate">{username}</p>
            <p className="text-xs text-on-surface-variant truncate capitalize">{roleLabel}</p>
          </div>

          <UserMenuItem
            label={t('settings')}
            icon="settings"
            onClick={handleSettingsClick}
          />

          <div className="h-px bg-outline-variant/30 mx-2 my-1" />

          <UserMenuItem
            label={t('log_out')}
            icon="logout"
            onClick={handleLogoutClick}
            isError
          />
        </div>
      )}
    </div>
  );
};
