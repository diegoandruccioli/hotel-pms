import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import type { Role } from '../types/auth.types';
import { AdminUsers } from './AdminUsers';
import { userService } from '../services/userService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)), key,
        );
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/userService', () => ({
  userService: {
    listUsers: vi.fn(),
    createUser: vi.fn(),
    activateUser: vi.fn(),
    deactivateUser: vi.fn(),
    resetUserPassword: vi.fn(),
  },
}));

const mockAddToast = vi.fn();

vi.mock('../store/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

const USER_ACTIVE = {
  id: 'u1', username: 'alice', email: 'alice@hotel.com',
  role: 'RECEPTIONIST' as Role, active: true, mustChangePassword: false, createdAt: '',
};

const USER_INACTIVE = {
  id: 'u2', username: 'bob', email: 'bob@hotel.com',
  role: 'OWNER' as Role, active: false, mustChangePassword: false, createdAt: '',
};

describe('AdminUsers', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAddToast.mockClear();
  });

  it('renders page heading', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([]);
    render(<AdminUsers />);
    await waitFor(() => expect(screen.getByText('page_title')).toBeInTheDocument());
  });

  it('renders user rows after load', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
    render(<AdminUsers />);
    await waitFor(() => expect(screen.getByText('alice')).toBeInTheDocument());
    expect(screen.getByText('alice@hotel.com')).toBeInTheDocument();
    expect(screen.getByText('RECEPTIONIST')).toBeInTheDocument();
  });

  it('shows active/inactive status for each user', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE, USER_INACTIVE]);
    render(<AdminUsers />);
    await waitFor(() => expect(screen.getByText('status_active')).toBeInTheDocument());
    expect(screen.getByText('status_inactive')).toBeInTheDocument();
  });

  it('opens create user modal on button click', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([]);
    render(<AdminUsers />);
    // Wait for loading to fully complete (no_users appears after setLoading(false))
    // This ensures the listUsers promise is flushed before we proceed.
    await waitFor(() => screen.getByText('no_users'));
    fireEvent.click(screen.getByText('btn_new_user'));
    await waitFor(() =>
      expect(screen.getByText('modal_create_title')).toBeInTheDocument()
    );
  });

  it('closes create user modal on cancel', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([]);
    render(<AdminUsers />);
    await waitFor(() => screen.getByText('btn_new_user'));
    fireEvent.click(screen.getByText('btn_new_user'));
    fireEvent.click(screen.getByText('btn_cancel'));
    expect(screen.queryByText('modal_create_title')).not.toBeInTheDocument();
  });

  it('calls deactivateUser when active user toggle clicked', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
    vi.mocked(userService.deactivateUser).mockResolvedValue({ ...USER_ACTIVE, active: false } as never);
    render(<AdminUsers />);
    await waitFor(() => screen.getByText('alice'));
    fireEvent.click(screen.getByText('btn_deactivate'));
    await waitFor(() => expect(userService.deactivateUser).toHaveBeenCalledWith('u1'));
  });

  it('calls activateUser when inactive user toggle clicked', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_INACTIVE]);
    vi.mocked(userService.activateUser).mockResolvedValue({ ...USER_INACTIVE, active: true } as never);
    render(<AdminUsers />);
    await waitFor(() => screen.getByText('bob'));
    fireEvent.click(screen.getByText('btn_activate'));
    await waitFor(() => expect(userService.activateUser).toHaveBeenCalledWith('u2'));
  });

  it('shows mustChangePassword warning for flagged users', async () => {
    const userWithFlag = { ...USER_ACTIVE, mustChangePassword: true };
    vi.mocked(userService.listUsers).mockResolvedValue([userWithFlag]);
    render(<AdminUsers />);
    await waitFor(() => expect(screen.getByText('must_change_pw')).toBeInTheDocument());
  });

  it('passes axe accessibility check', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
    const { container } = render(<AdminUsers />);
    await waitFor(() => screen.getByText('alice'));
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);

  it('shows an error toast when listUsers fails', async () => {
    vi.mocked(userService.listUsers).mockRejectedValue(new Error('boom'));
    render(<AdminUsers />);
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_load_failed', 'error'));
  });

  it('shows the no_users message when the list is empty', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([]);
    render(<AdminUsers />);
    await waitFor(() => expect(screen.getByText('no_users')).toBeInTheDocument());
  });

  it('shows an error toast when activate/deactivate fails', async () => {
    vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
    vi.mocked(userService.deactivateUser).mockRejectedValue(new Error('fail'));
    render(<AdminUsers />);
    await waitFor(() => screen.getByText('alice'));
    fireEvent.click(screen.getByText('btn_deactivate'));
    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('err_toggle_failed', 'error'));
  });

  describe('CreateUserModal', () => {
    it('shows a validation error when required fields are missing', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('btn_new_user'));
      fireEvent.click(screen.getByText('btn_new_user'));

      fireEvent.click(screen.getByText('btn_create'));
      expect(await screen.findByText('err_all_fields_required')).toBeInTheDocument();
      expect(userService.createUser).not.toHaveBeenCalled();
    });

    it('creates a user and shows a success toast', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([]);
      const created = { ...USER_ACTIVE, id: 'u3', username: 'carol' };
      vi.mocked(userService.createUser).mockResolvedValue(created as never);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('btn_new_user'));
      fireEvent.click(screen.getByText('btn_new_user'));

      fireEvent.change(screen.getByLabelText('label_username'), { target: { value: 'carol' } });
      fireEvent.change(screen.getByLabelText('label_email'), { target: { value: 'carol@hotel.com' } });
      fireEvent.change(screen.getByLabelText('label_password'), { target: { value: 'Secret123!' } });
      fireEvent.change(screen.getByLabelText('label_role'), { target: { value: 'OWNER' } });
      fireEvent.click(screen.getByText('btn_create'));

      await waitFor(() => expect(userService.createUser).toHaveBeenCalledWith({
        username: 'carol', email: 'carol@hotel.com', password: 'Secret123!', role: 'OWNER',
      }));
      expect(mockAddToast).toHaveBeenCalledWith('toast_created', 'success');
      expect(screen.queryByText('modal_create_title')).not.toBeInTheDocument();
    });

    it('shows an error when createUser fails', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([]);
      vi.mocked(userService.createUser).mockRejectedValue(new Error('fail'));
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('btn_new_user'));
      fireEvent.click(screen.getByText('btn_new_user'));

      fireEvent.change(screen.getByLabelText('label_username'), { target: { value: 'carol' } });
      fireEvent.change(screen.getByLabelText('label_email'), { target: { value: 'carol@hotel.com' } });
      fireEvent.change(screen.getByLabelText('label_password'), { target: { value: 'Secret123!' } });
      fireEvent.click(screen.getByText('btn_create'));

      expect(await screen.findByText('err_create_failed')).toBeInTheDocument();
    });

    it('toggles password visibility in the create user form', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('btn_new_user'));
      fireEvent.click(screen.getByText('btn_new_user'));

      const input = screen.getByLabelText('label_password');
      expect(input).toHaveAttribute('type', 'password');

      fireEvent.click(screen.getByLabelText('show_password'));
      expect(input).toHaveAttribute('type', 'text');

      fireEvent.click(screen.getByLabelText('hide_password'));
      expect(input).toHaveAttribute('type', 'password');
    });

    it('closes on Escape key', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('btn_new_user'));
      fireEvent.click(screen.getByText('btn_new_user'));
      fireEvent.keyDown(screen.getByText('modal_create_title').closest('div')!, { key: 'Escape' });
      expect(screen.queryByText('modal_create_title')).not.toBeInTheDocument();
    });
  });

  describe('ResetPasswordModal', () => {
    it('opens via the reset-password button', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));
      expect(screen.getByText('modal_reset_title')).toBeInTheDocument();
    });

    it('toggles password visibility independently for new and confirm fields', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      const newPwInput = screen.getByLabelText('label_new_password');
      const confirmPwInput = screen.getByLabelText('label_confirm_password');
      expect(newPwInput).toHaveAttribute('type', 'password');
      expect(confirmPwInput).toHaveAttribute('type', 'password');

      const [showNewToggle, showConfirmToggle] = screen.getAllByLabelText('show_password');
      fireEvent.click(showNewToggle);
      expect(newPwInput).toHaveAttribute('type', 'text');
      expect(confirmPwInput).toHaveAttribute('type', 'password');

      fireEvent.click(showConfirmToggle);
      expect(confirmPwInput).toHaveAttribute('type', 'text');
    });

    it('shows an error when the password is too short', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: 'short' } });
      fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

      expect(await screen.findByText('err_password_too_short')).toBeInTheDocument();
      expect(userService.resetUserPassword).not.toHaveBeenCalled();
    });

    it('shows an error when the password is long but too weak', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: 'aaaaaaaaaaaaaaaa' } });
      fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

      expect(await screen.findByText('err_password_too_weak')).toBeInTheDocument();
    });

    it('shows an error when passwords do not match', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: 'Secret123!!ABCDEF' } });
      fireEvent.change(screen.getByLabelText('label_confirm_password'), { target: { value: 'Different123!!ABC' } });
      fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

      expect(await screen.findByText('err_passwords_mismatch')).toBeInTheDocument();
      expect(userService.resetUserPassword).not.toHaveBeenCalled();
    });

    it('resets the password successfully and shows a success toast', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      vi.mocked(userService.resetUserPassword).mockResolvedValue(undefined);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      const strongPw = 'Secret123!!ABCDEF';
      fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: strongPw } });
      fireEvent.change(screen.getByLabelText('label_confirm_password'), { target: { value: strongPw } });
      fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

      await waitFor(() => expect(userService.resetUserPassword).toHaveBeenCalledWith('u1', strongPw));
      expect(mockAddToast).toHaveBeenCalledWith('toast_reset_success', 'success');
      expect(screen.queryByText('modal_reset_title')).not.toBeInTheDocument();
    });

    it('shows an error when resetUserPassword fails', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      vi.mocked(userService.resetUserPassword).mockRejectedValue(new Error('fail'));
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));

      const strongPw = 'Secret123!!ABCDEF';
      fireEvent.change(screen.getByLabelText('label_new_password'), { target: { value: strongPw } });
      fireEvent.change(screen.getByLabelText('label_confirm_password'), { target: { value: strongPw } });
      fireEvent.click(screen.getByRole('button', { name: 'btn_reset_password' }));

      expect(await screen.findByText('err_reset_failed')).toBeInTheDocument();
    });

    it('closes via cancel and Escape', async () => {
      vi.mocked(userService.listUsers).mockResolvedValue([USER_ACTIVE]);
      render(<AdminUsers />);
      await waitFor(() => screen.getByText('alice'));
      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));
      fireEvent.click(screen.getByText('btn_cancel'));
      expect(screen.queryByText('modal_reset_title')).not.toBeInTheDocument();

      fireEvent.click(screen.getByLabelText('btn_reset_password alice'));
      fireEvent.keyDown(screen.getByText('modal_reset_title').closest('div')!, { key: 'Escape' });
      expect(screen.queryByText('modal_reset_title')).not.toBeInTheDocument();
    });
  });
});
