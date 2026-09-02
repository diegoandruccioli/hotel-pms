import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { ResetPasswordModal } from './ResetPasswordModal';
import { userService } from '../../services/userService';
import type { UserResponse } from '../../types';
import type { Role } from '../../types';

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

vi.mock('../../services/userService', () => ({
  userService: { resetUserPassword: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const USER: UserResponse = {
  id: 'u1', username: 'alice', email: 'alice@hotel.com',
  role: 'RECEPTIONIST' as Role, active: true, mustChangePassword: false, createdAt: '',
};

describe('ResetPasswordModal', () => {
  const onClose = vi.fn();
  const onSuccess = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders the reset form for the given user', () => {
    render(<ResetPasswordModal user={USER} onClose={onClose} onSuccess={onSuccess} />);
    expect(screen.getByText('modal_reset_title')).toBeInTheDocument();
    expect(screen.getByLabelText('label_new_password')).toBeInTheDocument();
    expect(screen.getByLabelText('label_confirm_password')).toBeInTheDocument();
  });

  it('calls onClose when cancel is clicked', () => {
    render(<ResetPasswordModal user={USER} onClose={onClose} onSuccess={onSuccess} />);
    fireEvent.click(screen.getByText('btn_cancel'));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onSuccess after a successful reset', async () => {
    vi.mocked(userService.resetUserPassword).mockResolvedValue(undefined);
    render(<ResetPasswordModal user={USER} onClose={onClose} onSuccess={onSuccess} />);

    const strongPw = 'Secret123!!ABCDEF';
    fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: strongPw } });
    fireEvent.change(screen.getByLabelText('label_confirm_password'), { target: { value: strongPw } });
    fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

    await waitFor(() => expect(userService.resetUserPassword).toHaveBeenCalledWith('u1', strongPw));
    expect(onSuccess).toHaveBeenCalled();
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<ResetPasswordModal user={USER} onClose={onClose} onSuccess={onSuccess} />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
