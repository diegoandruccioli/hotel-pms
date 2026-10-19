/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { M3RowActionsMenu, type RowAction } from './M3RowActionsMenu';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const makeItems = (overrides: Partial<Record<string, Partial<RowAction>>> = {}): RowAction[] => [
  { id: 'view', label: 'View', icon: 'visibility', onSelect: vi.fn(), ...overrides.view },
  { id: 'edit', label: 'Edit', icon: 'edit', onSelect: vi.fn(), ...overrides.edit },
  { id: 'delete', label: 'Delete', icon: 'delete', tone: 'error', onSelect: vi.fn(), testId: 'delete-item', ...overrides.delete },
];

const trigger = () => screen.getByRole('button', { name: 'Actions for Marco' });
const menu = () => screen.queryByRole('menu');

describe('M3RowActionsMenu', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('is closed by default and exposes menu-button semantics', () => {
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    expect(trigger()).toHaveAttribute('aria-haspopup', 'menu');
    expect(trigger()).toHaveAttribute('aria-expanded', 'false');
    expect(menu()).not.toBeInTheDocument();
  });

  it('opens on click, focuses the first item and links the trigger to the menu', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    expect(trigger()).toHaveAttribute('aria-expanded', 'true');
    expect(menu()).toHaveAttribute('id', trigger().getAttribute('aria-controls'));
    expect(screen.getAllByRole('menuitem')).toHaveLength(3);
    expect(screen.getByRole('menuitem', { name: 'View' })).toHaveFocus();
  });

  it('toggles closed on a second trigger click', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    await user.click(trigger());
    expect(menu()).not.toBeInTheDocument();
  });

  it('opens with ArrowDown on the first item and ArrowUp on the last', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    trigger().focus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'View' })).toHaveFocus();
    await user.keyboard('{Escape}');
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus();
  });

  it('moves focus with Arrow, Home and End, wrapping around', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'Edit' })).toHaveFocus();
    await user.keyboard('{End}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('menuitem', { name: 'View' })).toHaveFocus();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveFocus();
    await user.keyboard('{Home}');
    expect(screen.getByRole('menuitem', { name: 'View' })).toHaveFocus();
  });

  it('closes on Escape and returns focus to the trigger', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    await user.keyboard('{Escape}');
    expect(menu()).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
  });

  it('closes on Tab and keeps focus on the trigger', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    fireEvent.keyDown(screen.getByRole('menuitem', { name: 'View' }), { key: 'Tab' });
    expect(menu()).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
  });

  it('closes on an outside press but not on a press inside the menu', async () => {
    const user = userEvent.setup();
    render(
      <div>
        <M3RowActionsMenu label="Actions for Marco" items={makeItems()} />
        <p>outside</p>
      </div>,
    );
    await user.click(trigger());
    fireEvent.pointerDown(screen.getByRole('menu'));
    expect(menu()).toBeInTheDocument();
    fireEvent.pointerDown(screen.getByText('outside'));
    expect(menu()).not.toBeInTheDocument();
  });

  it('follows the trigger on scroll and resize instead of closing', async () => {
    const rectAt = (top: number) => ({
      left: 0, right: 1000, top, bottom: top + 40, width: 40, height: 40, x: 0, y: top, toJSON: () => ({}),
    }) as DOMRect;
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(rectAt(10));
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    expect(screen.getByRole('menu').style.top).toBe('54px');

    spy.mockReturnValue(rectAt(110));
    fireEvent.scroll(window);
    expect(menu()).toBeInTheDocument();
    expect(screen.getByRole('menu').style.top).toBe('154px');

    spy.mockReturnValue(rectAt(210));
    fireEvent(window, new Event('resize'));
    expect(screen.getByRole('menu').style.top).toBe('254px');
  });

  it('closes on scroll once the trigger has left the viewport', async () => {
    const spy = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      left: 0, right: 1000, top: 10, bottom: 50, width: 40, height: 40, x: 0, y: 10, toJSON: () => ({}),
    } as DOMRect);
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    spy.mockReturnValue({
      left: 0, right: 1000, top: -80, bottom: -40, width: 40, height: 40, x: 0, y: -80, toJSON: () => ({}),
    } as DOMRect);
    fireEvent.scroll(window);
    expect(menu()).not.toBeInTheDocument();
  });

  it('runs the action, closes the menu and refocuses the trigger', async () => {
    const user = userEvent.setup();
    const items = makeItems();
    render(<M3RowActionsMenu label="Actions for Marco" items={items} />);
    await user.click(trigger());
    await user.click(screen.getByRole('menuitem', { name: 'Edit' }));
    expect(items[1]?.onSelect).toHaveBeenCalledTimes(1);
    expect(menu()).not.toBeInTheDocument();
    expect(trigger()).toHaveFocus();
  });

  it('keeps a disabled item focusable but inert', async () => {
    const user = userEvent.setup();
    const items = makeItems({ edit: { disabled: true } });
    render(<M3RowActionsMenu label="Actions for Marco" items={items} />);
    await user.click(trigger());
    const edit = screen.getByRole('menuitem', { name: 'Edit' });
    expect(edit).toHaveAttribute('aria-disabled', 'true');
    await user.click(edit);
    expect(items[1]?.onSelect).not.toHaveBeenCalled();
    expect(menu()).toBeInTheDocument();
  });

  it('puts a divider before the first destructive item and exposes its test id', async () => {
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    expect(screen.getAllByRole('separator')).toHaveLength(1);
    expect(screen.getByTestId('delete-item')).toHaveClass('text-error');
  });

  it('draws no divider when every item is destructive', async () => {
    const user = userEvent.setup();
    const items: RowAction[] = [
      { id: 'a', label: 'A', tone: 'error', onSelect: vi.fn() },
      { id: 'b', label: 'B', tone: 'error', onSelect: vi.fn() },
    ];
    render(<M3RowActionsMenu label="Actions for Marco" items={items} />);
    await user.click(trigger());
    expect(screen.queryByRole('separator')).not.toBeInTheDocument();
  });

  it('renders nothing without items', () => {
    const { container } = render(<M3RowActionsMenu label="Actions for Marco" items={[]} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a lone action inline instead of a one-item menu', async () => {
    const user = userEvent.setup();
    const items: RowAction[] = [{ id: 'view', label: 'View', onSelect: vi.fn(), testId: 'lone' }];
    render(<M3RowActionsMenu label="Actions for Marco" items={items} />);
    expect(screen.queryByRole('button', { name: 'Actions for Marco' })).not.toBeInTheDocument();
    await user.click(screen.getByTestId('lone'));
    expect(items[0]?.onSelect).toHaveBeenCalledTimes(1);
  });

  it('opens upward when there is no room below the trigger', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      left: 0, right: 1000, top: window.innerHeight - 60, bottom: window.innerHeight - 20,
      width: 40, height: 40, x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect);
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    const style = screen.getByRole('menu').style;
    expect(style.bottom).not.toBe('');
    expect(style.top).toBe('');
  });

  it('opens downward when there is room', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      left: 0, right: 1000, top: 10, bottom: 50, width: 40, height: 40, x: 0, y: 10, toJSON: () => ({}),
    } as DOMRect);
    const user = userEvent.setup();
    render(<M3RowActionsMenu label="Actions for Marco" items={makeItems()} />);
    await user.click(trigger());
    const style = screen.getByRole('menu').style;
    expect(style.top).toBe('54px');
    expect(style.bottom).toBe('');
  });

  it('has no axe violations open or closed', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <main>
        <M3RowActionsMenu label="Actions for Marco" items={makeItems()} />
      </main>,
    );
    expect(await axe(container)).toHaveNoViolations();
    await user.click(trigger());
    // The menu is portalled to <body>, outside any landmark; that is the page's concern, not the menu's.
    expect(await axe(document.body, { rules: { region: { enabled: false } } })).toHaveNoViolations();
  });
});
