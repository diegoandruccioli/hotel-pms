import { memo, useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { useEscapeKey } from '../../hooks/useEscapeKey';
import { cn } from '../../utils';
import { MaterialIcon } from '../MaterialIcon';
import { M3TableActionLink } from './M3TableActionLink';
import { M3Tooltip } from './M3Tooltip';

export interface RowAction {
  id: string;
  label: string;
  /** Material Symbol name. */
  icon?: string;
  onSelect: () => void;
  /** `error` is for destructive actions: they sit below a divider, after the default ones. */
  tone?: 'default' | 'error';
  /** Stays focusable (`aria-disabled`) but does nothing. */
  disabled?: boolean;
  testId?: string;
}

interface M3RowActionsMenuProps {
  /** Accessible name of the trigger, naming the row: "Actions for Marco Bianchi". */
  label: string;
  items: RowAction[];
}

interface MenuPosition {
  right: number;
  top?: number;
  bottom?: number;
}

const ITEM_HEIGHT_PX = 44;
const MENU_PADDING_PX = 16;
const GAP_PX = 4;

const itemToneClasses: Record<NonNullable<RowAction['tone']>, string> = {
  default: 'text-on-surface hover:bg-surface-container-highest focus-visible:bg-surface-container-highest focus-visible:ring-primary',
  error: 'text-error hover:bg-error-container/20 focus-visible:bg-error-container/20 focus-visible:ring-error',
};

interface RowMenuItemProps {
  item: RowAction;
  onSelect: (item: RowAction) => void;
}

const RowMenuItem = memo(({ item, onSelect }: RowMenuItemProps) => {
  const handleClick = useCallback(() => onSelect(item), [onSelect, item]);
  const tone = item.tone ?? 'default';

  return (
    <button
      type="button"
      role="menuitem"
      aria-disabled={item.disabled || undefined}
      data-testid={item.testId}
      onClick={handleClick}
      className={cn(
        'flex items-center gap-3 w-full min-h-11 px-4 text-sm font-body text-left transition-colors',
        'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-inset',
        itemToneClasses[tone],
        item.disabled && 'opacity-38 cursor-not-allowed',
      )}
    >
      {item.icon && (
        <MaterialIcon
          name={item.icon}
          size={20}
          className={tone === 'error' ? 'text-error' : 'text-on-surface-variant'}
        />
      )}
      {item.label}
    </button>
  );
});
RowMenuItem.displayName = 'RowMenuItem';

/** A menu of one item is an anti-pattern: the lone action stays a visible inline link. */
const InlineAction = memo(({ item }: { item: RowAction }) => (
  <M3TableActionLink
    tone={item.tone === 'error' ? 'error' : 'primary'}
    disabled={item.disabled}
    data-testid={item.testId}
    onClick={item.onSelect}
  >
    {item.label}
  </M3TableActionLink>
));
InlineAction.displayName = 'InlineAction';

/**
 * Overflow menu for a table row's secondary actions (WAI-ARIA menu button). The surface is
 * rendered in a portal with `position: fixed`, so a clipping table wrapper can't cut it off,
 * and it flips upward when there is no room below. Opens on click/Enter/Space/ArrowDown
 * (first item) or ArrowUp (last item); Arrow/Home/End move, Escape and Tab close, and
 * focus returns to the trigger before an action runs. Closes on outside press, scroll and resize.
 * Fewer than two items is no menu: none renders nothing, one renders that action inline.
 */
export const M3RowActionsMenu = memo(({ label, items }: M3RowActionsMenuProps) => {
  const { t } = useTranslation('common');
  const menuId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const initialFocus = useRef<'first' | 'last'>('first');
  const [position, setPosition] = useState<MenuPosition | null>(null);
  const open = position !== null;

  const firstErrorIndex = useMemo(() => {
    const index = items.findIndex((item) => item.tone === 'error');
    return index > 0 ? index : -1;
  }, [items]);

  const close = useCallback(() => setPosition(null), []);

  const openMenu = useCallback((focus: 'first' | 'last') => {
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    initialFocus.current = focus;
    const right = window.innerWidth - rect.right;
    const menuHeight = items.length * ITEM_HEIGHT_PX + MENU_PADDING_PX;
    const fitsBelow = rect.bottom + GAP_PX + menuHeight <= window.innerHeight;
    setPosition(
      fitsBelow
        ? { right, top: rect.bottom + GAP_PX }
        : { right, bottom: window.innerHeight - rect.top + GAP_PX },
    );
  }, [items.length]);

  const handleTriggerClick = useCallback(() => {
    if (open) close();
    else openMenu('first');
  }, [open, close, openMenu]);

  const handleTriggerKeyDown = useCallback((e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    e.preventDefault();
    openMenu(e.key === 'ArrowDown' ? 'first' : 'last');
  }, [openMenu]);

  const handleEscape = useCallback(() => {
    close();
    triggerRef.current?.focus();
  }, [close]);
  useEscapeKey(open, handleEscape);

  // Move focus into the menu once it is mounted.
  useEffect(() => {
    if (!open) return;
    const menuItems = menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]');
    if (!menuItems?.length) return;
    menuItems[initialFocus.current === 'last' ? menuItems.length - 1 : 0]?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: PointerEvent) => {
      const target = e.target as Node;
      if (menuRef.current?.contains(target) || triggerRef.current?.contains(target)) return;
      close();
    };
    document.addEventListener('pointerdown', handlePointerDown);
    // Capture phase: also catches scrolling of nested containers.
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [open, close]);

  const handleMenuKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Tab') {
      close();
      triggerRef.current?.focus();
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) return;
    e.preventDefault();
    const menuItems = Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    if (menuItems.length === 0) return;
    const index = menuItems.indexOf(document.activeElement as HTMLElement);
    let next = 0;
    if (e.key === 'ArrowDown') next = (index + 1) % menuItems.length;
    else if (e.key === 'ArrowUp') next = (index - 1 + menuItems.length) % menuItems.length;
    else if (e.key === 'End') next = menuItems.length - 1;
    menuItems[next]?.focus();
  }, [close]);

  const handleSelect = useCallback((item: RowAction) => {
    if (item.disabled) return;
    close();
    triggerRef.current?.focus();
    item.onSelect();
  }, [close]);

  if (items.length === 0) return null;
  if (items[0] && items.length === 1) return <InlineAction item={items[0]} />;

  return (
    <>
      <M3Tooltip label={t('row_actions')} disabled={open}>
        <button
          ref={triggerRef}
          type="button"
          aria-label={label}
          aria-haspopup="menu"
          aria-expanded={open}
          aria-controls={open ? menuId : undefined}
          onClick={handleTriggerClick}
          onKeyDown={handleTriggerKeyDown}
          className={cn(
            'flex items-center justify-center w-10 h-10 rounded-shape-full cursor-pointer',
            'text-on-surface-variant hover:bg-surface-container-highest transition-colors',
            'focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2',
            open && 'bg-surface-container-highest text-on-surface',
          )}
        >
          <MaterialIcon name="more_vert" size={24} />
        </button>
      </M3Tooltip>
      {position &&
        createPortal(
          <div
            ref={menuRef}
            id={menuId}
            role="menu"
            tabIndex={-1}
            aria-label={label}
            onKeyDown={handleMenuKeyDown}
            style={position}
            className="fixed z-50 w-56 py-2 overflow-hidden bg-surface-container rounded-shape-lg shadow-elevation-3 border border-outline-variant/30 animate-fade-in"
          >
            {items.map((item, index) => (
              <div key={item.id}>
                {index === firstErrorIndex && (
                  <div role="separator" className="h-px bg-outline-variant/30 mx-2 my-1" />
                )}
                <RowMenuItem item={item} onSelect={handleSelect} />
              </div>
            ))}
          </div>,
          document.body,
        )}
    </>
  );
});
M3RowActionsMenu.displayName = 'M3RowActionsMenu';
