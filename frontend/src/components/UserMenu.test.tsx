import { describe, it, expect, vi } from 'vitest';
import { useCallback, useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { UserMenu } from './UserMenu';

const t = vi.hoisted(() => (key: string, opts?: Record<string, unknown>) => {
  if (opts && typeof opts === 'object') {
    return Object.entries(opts).reduce((out, [k, v]) => out.replace(`{{${k}}}`, String(v)), key);
  }
  return key;
});
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const baseProps = {
  username: 'admin',
  roleLabel: 'Amministratore',
  onToggle: vi.fn(),
  onClose: vi.fn(),
  onOpenSettings: vi.fn(),
  onLogout: vi.fn(),
};

const seen: Element[] = [];
const recordFocus = () => { if (document.activeElement) seen.push(document.activeElement); };

describe('UserMenu', () => {
  it('renders the avatar trigger with the user initial', () => {
    render(<UserMenu {...baseProps} open={false} />);
    expect(screen.getByText('A')).toBeInTheDocument();
  });

  it('does not render the menu when closed', () => {
    render(<UserMenu {...baseProps} open={false} />);
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();
  });

  it('renders exactly 2 menu items when open: settings and log out', () => {
    render(<UserMenu {...baseProps} open />);
    const items = screen.getAllByRole('menuitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('settings');
    expect(items[1]).toHaveTextContent('log_out');
  });

  it('does not render a "my profile" entry', () => {
    render(<UserMenu {...baseProps} open />);
    expect(screen.queryByText('my_profile')).not.toBeInTheDocument();
  });

  it('calls onClose and onOpenSettings when Settings is clicked', () => {
    const onClose = vi.fn();
    const onOpenSettings = vi.fn();
    render(<UserMenu {...baseProps} open onClose={onClose} onOpenSettings={onOpenSettings} />);
    fireEvent.click(screen.getByRole('menuitem', { name: /settings/ }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onOpenSettings).toHaveBeenCalledTimes(1);
  });

  it('calls onClose and onLogout when Log out is clicked', () => {
    const onClose = vi.fn();
    const onLogout = vi.fn();
    render(<UserMenu {...baseProps} open onClose={onClose} onLogout={onLogout} />);
    fireEvent.click(screen.getByRole('menuitem', { name: /log_out/ }));
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  it('calls onToggle when the avatar trigger is clicked', () => {
    const onToggle = vi.fn();
    render(<UserMenu {...baseProps} open={false} onToggle={onToggle} />);
    fireEvent.click(screen.getByRole('button', { name: 'user_menu_label' }));
    expect(onToggle).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape key while open', () => {
    const onClose = vi.fn();
    render(<UserMenu {...baseProps} open onClose={onClose} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on outside pointerdown while open', () => {
    const onClose = vi.fn();
    render(
      <div>
        <UserMenu {...baseProps} open onClose={onClose} />
        <button type="button">outside</button>
      </div>
    );
    fireEvent.pointerDown(screen.getByText('outside'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should have no accessibility violations when open', async () => {
    const { container } = render(<UserMenu {...baseProps} open />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  describe('keyboard', () => {
    // Real open state, like MainLayout holds it: focus moves only if the component asks for it
    const Controlled = ({ onOpenSettings = vi.fn(), onLogout = vi.fn() }: { onOpenSettings?: () => void; onLogout?: () => void }) => {
      const [open, setOpen] = useState(false);
      const toggle = useCallback(() => setOpen((v) => !v), []);
      const close = useCallback(() => setOpen(false), []);
      return (
        <UserMenu
          {...baseProps}
          open={open}
          onToggle={toggle}
          onClose={close}
          onOpenSettings={onOpenSettings}
          onLogout={onLogout}
        />
      );
    };
    const trigger = () => screen.getByRole('button', { name: 'user_menu_label' });
    const item = (name: RegExp) => screen.getByRole('menuitem', { name });

    it('opens on click with focus on the first item', () => {
      render(<Controlled />);
      fireEvent.click(trigger());
      expect(item(/settings/)).toHaveFocus();
    });

    it('opens on ArrowDown from the trigger with focus on the first item', () => {
      render(<Controlled />);
      trigger().focus();
      fireEvent.keyDown(trigger(), { key: 'ArrowDown' });
      expect(item(/settings/)).toHaveFocus();
    });

    it('opens on ArrowUp from the trigger with focus on the last item', () => {
      render(<Controlled />);
      trigger().focus();
      fireEvent.keyDown(trigger(), { key: 'ArrowUp' });
      expect(item(/log_out/)).toHaveFocus();
    });

    it('moves with ArrowDown/ArrowUp (wrapping), Home and End', () => {
      render(<Controlled />);
      fireEvent.click(trigger());
      fireEvent.keyDown(item(/settings/), { key: 'ArrowDown' });
      expect(item(/log_out/)).toHaveFocus();
      fireEvent.keyDown(item(/log_out/), { key: 'ArrowDown' });
      expect(item(/settings/)).toHaveFocus();
      fireEvent.keyDown(item(/settings/), { key: 'ArrowUp' });
      expect(item(/log_out/)).toHaveFocus();
      fireEvent.keyDown(item(/log_out/), { key: 'Home' });
      expect(item(/settings/)).toHaveFocus();
      fireEvent.keyDown(item(/settings/), { key: 'End' });
      expect(item(/log_out/)).toHaveFocus();
    });

    it('closes on Tab and gives focus back to the trigger', () => {
      render(<Controlled />);
      fireEvent.click(trigger());
      fireEvent.keyDown(item(/settings/), { key: 'Tab' });
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();
      expect(trigger()).toHaveFocus();
    });

    it('closes on Escape and gives focus back to the trigger', () => {
      render(<Controlled />);
      fireEvent.click(trigger());
      fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();
      expect(trigger()).toHaveFocus();
    });

    it('has focus back on the trigger by the time the chosen action runs', () => {
      seen.length = 0;
      render(<Controlled onOpenSettings={recordFocus} onLogout={recordFocus} />);
      fireEvent.click(trigger());
      fireEvent.click(item(/settings/));
      fireEvent.click(trigger());
      fireEvent.click(item(/log_out/));
      expect(seen).toEqual([trigger(), trigger()]);
    });
  });
});
