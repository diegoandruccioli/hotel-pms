import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useEscapeKey } from '../../hooks/useEscapeKey';
import { cn } from '../../utils';

const SHOW_DELAY_MS = 300;
/** Lets the pointer cross the gap between the trigger and the tooltip. */
const HIDE_DELAY_MS = 100;
const OFFSET_PX = 8;

interface M3TooltipProps {
  /** Visible name of the control. */
  label: string;
  /** A single focusable control. Its accessible name must already exist: the tooltip is visual only. */
  children: React.ReactNode;
  /** Classes of the wrapper while enabled; it is also the hover target, so keep it as tight as the control. */
  className?: string;
  disabled?: boolean;
}

interface Position {
  left: number;
  top: number;
}

// Input modality, tracked once for the whole document (the same idea as the
// :focus-visible polyfill): focus only counts as "keyboard" if the last input was a key.
// A press — mouse or touch — also focuses the control, but hover already covers the
// mouse and a tap would leave the tooltip stuck open.
let keyboardModality = true;
let modalityTracked = false;

const trackModality = () => {
  if (modalityTracked) return;
  modalityTracked = true;
  document.addEventListener('keydown', () => { keyboardModality = true; }, true);
  document.addEventListener('pointerdown', () => { keyboardModality = false; }, true);
};

/**
 * Visible label for an icon-only control, shown to the right of it on mouse hover and
 * keyboard focus. Rendered in a portal with `position: fixed`, so a scrolling/clipping
 * ancestor (the sidebar) can't cut it off. WCAG 1.4.13: dismissible with Escape, stays
 * open while the pointer is over it or the control is focused, and goes away on
 * scroll/resize.
 *
 * Visual only (`aria-hidden`): the control keeps its own accessible name, so there is no
 * `aria-describedby` and no double announcement. Don't put the only copy of any
 * information in it. No touch behaviour: a tap activates the control.
 *
 * The wrapper stays mounted when `disabled` flips (as `display: contents`), so toggling
 * it never remounts the control or drops its focus.
 */
export const M3Tooltip = ({ label, children, className, disabled = false }: M3TooltipProps) => {
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const focused = useRef(false);
  const [position, setPosition] = useState<Position | null>(null);

  const clearTimer = useCallback(() => clearTimeout(timer.current), []);

  const hide = useCallback(() => {
    clearTimer();
    setPosition(null);
  }, [clearTimer]);

  const show = useCallback(() => {
    const rect = wrapperRef.current?.getBoundingClientRect();
    if (!rect) return;
    setPosition({ left: rect.right + OFFSET_PX, top: rect.top + rect.height / 2 });
  }, []);

  const handlePointerEnter = useCallback(
    (e: React.PointerEvent) => {
      if (e.pointerType !== 'mouse') return;
      clearTimer();
      timer.current = setTimeout(show, SHOW_DELAY_MS);
    },
    [clearTimer, show],
  );

  const handlePointerLeave = useCallback(
    (e: React.PointerEvent) => {
      if (e.pointerType !== 'mouse') return;
      clearTimer();
      // A tooltip opened by focus stays for as long as the control is focused.
      if (focused.current) return;
      timer.current = setTimeout(hide, HIDE_DELAY_MS);
    },
    [clearTimer, hide],
  );

  const handleFocus = useCallback(() => {
    focused.current = true;
    if (!keyboardModality) return;
    clearTimer();
    show();
  }, [clearTimer, show]);

  const handleBlur = useCallback(() => {
    focused.current = false;
    hide();
  }, [hide]);

  // Turning the tooltip off must not leave a stale one behind (it would reappear on re-enable).
  if (disabled && position) setPosition(null);

  const visible = position !== null;
  const style = useMemo(
    () => (position ? { left: position.left, top: position.top } : undefined),
    [position],
  );

  useEffect(trackModality, []);

  useEscapeKey(visible, hide);

  useEffect(() => {
    if (!visible) return;
    // Capture phase: also catches scrolling of nested containers.
    window.addEventListener('scroll', hide, true);
    window.addEventListener('resize', hide);
    return () => {
      window.removeEventListener('scroll', hide, true);
      window.removeEventListener('resize', hide);
    };
  }, [visible, hide]);

  useEffect(() => clearTimer, [clearTimer]);

  const handlers = disabled
    ? {}
    : {
        onPointerEnter: handlePointerEnter,
        onPointerLeave: handlePointerLeave,
        onFocus: handleFocus,
        onBlur: handleBlur,
      };

  return (
    <>
      <span
        ref={wrapperRef}
        role="presentation"
        className={disabled ? 'contents' : cn('flex', className)}
        {...handlers}
      >
        {children}
      </span>
      {position &&
        !disabled &&
        createPortal(
          <div
            role="presentation"
            aria-hidden="true"
            data-testid="m3-tooltip"
            className="fixed z-50 -translate-y-1/2 whitespace-nowrap rounded-shape-sm bg-inverse-surface px-3 py-1.5 text-sm font-medium font-body text-inverse-on-surface shadow-elevation-2"
            style={style}
            onPointerEnter={clearTimer}
            onPointerLeave={handlePointerLeave}
          >
            {label}
          </div>,
          document.body,
        )}
    </>
  );
};
