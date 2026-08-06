import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { UserRow } from './UserRow';
import type { UserResponse } from '../../types/user.types';
import type { Role } from '../../types/auth.types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

const USER_ACTIVE: UserResponse = {
  id: 'u1', username: 'alice', email: 'alice@hotel.com',
  role: 'RECEPTIONIST' as Role, active: true, mustChangePassword: false, createdAt: '',
};

const renderRow = (props: Partial<React.ComponentProps<typeof UserRow>> = {}) =>
  render(
    <table>
      <tbody>
        <UserRow
          user={USER_ACTIVE}
          onToggle={vi.fn()}
          onResetPassword={vi.fn()}
          currentUsername="admin"
          {...props}
        />
      </tbody>
    </table>,
  );

describe('UserRow', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders username, email and role', () => {
    renderRow();
    expect(screen.getByText('alice')).toBeInTheDocument();
    expect(screen.getByText('alice@hotel.com')).toBeInTheDocument();
    expect(screen.getByText('RECEPTIONIST')).toBeInTheDocument();
  });

  it('calls onToggle with the user when the status button is clicked', () => {
    const onToggle = vi.fn();
    renderRow({ onToggle });
    fireEvent.click(screen.getByText('btn_deactivate'));
    expect(onToggle).toHaveBeenCalledWith(USER_ACTIVE);
  });

  it('calls onResetPassword with the user when the reset button is clicked', () => {
    const onResetPassword = vi.fn();
    renderRow({ onResetPassword });
    fireEvent.click(screen.getByLabelText('btn_reset_password alice'));
    expect(onResetPassword).toHaveBeenCalledWith(USER_ACTIVE);
  });

  it('hides the reset-password button for the currently logged-in user', () => {
    renderRow({ currentUsername: 'alice' });
    expect(screen.queryByLabelText('btn_reset_password alice')).not.toBeInTheDocument();
  });

  it('shows the mustChangePassword warning when flagged', () => {
    renderRow({ user: { ...USER_ACTIVE, mustChangePassword: true } });
    expect(screen.getByText('must_change_pw')).toBeInTheDocument();
  });

  it('passes axe accessibility check', async () => {
    const { container } = renderRow();
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
