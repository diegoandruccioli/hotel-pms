import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { UserMenu } from './UserMenu';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce((s, [k, v]) => s.replace(`{{${k}}}`, String(v)), key);
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
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
});
