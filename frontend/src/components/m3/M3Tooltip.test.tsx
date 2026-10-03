import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { M3Tooltip } from './M3Tooltip';

const RECT = { left: 0, right: 80, top: 100, bottom: 140, width: 80, height: 40, x: 0, y: 100, toJSON: () => ({}) };

const Fixture = ({ disabled = false }: { disabled?: boolean }) => (
  <main>
    <M3Tooltip label="Guests" disabled={disabled}>
      <a href="/guests">
        <span className="sr-only">Guests</span>
      </a>
    </M3Tooltip>
    <button type="button">elsewhere</button>
  </main>
);

const tooltip = () => screen.queryByTestId('m3-tooltip');
const link = () => screen.getByRole('link');
const advance = (ms: number) => act(() => { vi.advanceTimersByTime(ms); });

describe('M3Tooltip', () => {
  beforeEach(() => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(RECT as DOMRect);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('is hidden by default', () => {
    render(<Fixture />);
    expect(tooltip()).not.toBeInTheDocument();
  });

  describe('mouse', () => {
    // fireEvent, not user-event: under fake timers user-event waits on a timer that never fires.
    const enter = (el: Element, pointerType = 'mouse') => fireEvent.pointerEnter(el, { pointerType });
    const leave = (el: Element, pointerType = 'mouse') => fireEvent.pointerLeave(el, { pointerType });
    const wrapper = () => link().parentElement as HTMLElement;

    beforeEach(() => vi.useFakeTimers());

    it('shows after a short delay, to the right of the control', () => {
      render(<Fixture />);
      enter(wrapper());
      expect(tooltip()).not.toBeInTheDocument();

      advance(300);

      expect(tooltip()).toHaveTextContent('Guests');
      expect(tooltip()).toHaveStyle({ left: '88px', top: '120px' });
    });

    it('hides shortly after the pointer leaves', () => {
      render(<Fixture />);
      enter(wrapper());
      advance(300);
      leave(wrapper());
      expect(tooltip()).toBeInTheDocument();

      advance(100);

      expect(tooltip()).not.toBeInTheDocument();
    });

    it('stays open while the pointer is over the tooltip (WCAG 1.4.13 hoverable)', () => {
      render(<Fixture />);
      enter(wrapper());
      advance(300);
      leave(wrapper());
      enter(tooltip() as HTMLElement);

      advance(500);

      expect(tooltip()).toBeInTheDocument();
    });

    it('ignores touch pointers', () => {
      render(<Fixture />);
      enter(wrapper(), 'touch');
      advance(500);
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('hides on scroll and on resize', () => {
      render(<Fixture />);
      enter(wrapper());
      advance(300);
      fireEvent.scroll(window);
      expect(tooltip()).not.toBeInTheDocument();

      enter(wrapper());
      advance(300);
      fireEvent(window, new Event('resize'));
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('does not show anything after unmounting mid-delay', () => {
      const { unmount } = render(<Fixture />);
      enter(wrapper());
      unmount();
      advance(500);
      expect(tooltip()).not.toBeInTheDocument();
    });
  });

  describe('keyboard', () => {
    it('shows immediately on focus', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.tab();
      expect(tooltip()).toHaveTextContent('Guests');
    });

    it('hides on blur', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.tab();
      await user.tab();
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('hides on Escape (WCAG 1.4.13 dismissible)', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.tab();
      await user.keyboard('{Escape}');
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('stays open while the control is focused even if the pointer passes over and leaves (WCAG 1.4.13 persistent)', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.tab();
      await user.hover(link());
      await user.unhover(link());
      await new Promise((resolve) => setTimeout(resolve, 200));
      expect(tooltip()).toBeInTheDocument();
    });

    it('does not open after a mouse click that focuses the control', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.click(link());
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('opens on keyboard focus again after a click on an already-focused control', async () => {
      const user = userEvent.setup();
      render(<Fixture />);
      await user.tab();
      await user.click(link());
      await user.tab();
      await user.tab({ shift: true });
      expect(tooltip()).toHaveTextContent('Guests');
    });
  });

  describe('disabled', () => {
    it('shows nothing', async () => {
      const user = userEvent.setup();
      render(<Fixture disabled />);
      await user.tab();
      expect(tooltip()).not.toBeInTheDocument();
    });

    it('keeps the same element and its focus when toggled', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<Fixture />);
      await user.tab();
      const before = link();
      expect(before).toHaveFocus();

      rerender(<Fixture disabled />);

      expect(link()).toBe(before);
      expect(link()).toHaveFocus();
    });

    it('drops a visible tooltip when disabled and does not bring it back when re-enabled', async () => {
      const user = userEvent.setup();
      const { rerender } = render(<Fixture />);
      await user.tab();
      expect(tooltip()).toBeInTheDocument();

      rerender(<Fixture disabled />);
      expect(tooltip()).not.toBeInTheDocument();

      rerender(<Fixture />);
      expect(tooltip()).not.toBeInTheDocument();
    });
  });

  it('is visual only: aria-hidden, no title and no aria-describedby on the control', async () => {
    const user = userEvent.setup();
    render(<Fixture />);
    await user.tab();

    expect(tooltip()).toHaveAttribute('aria-hidden', 'true');
    expect(link()).not.toHaveAttribute('title');
    expect(link()).not.toHaveAttribute('aria-describedby');
  });

  it('should have no accessibility violations', async () => {
    const user = userEvent.setup();
    const { container } = render(<Fixture />);
    await user.tab();
    expect(await axe(container)).toHaveNoViolations();
    // The portal lives outside `container`: check it too.
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
