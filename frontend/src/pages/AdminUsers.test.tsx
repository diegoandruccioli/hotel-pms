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
    await waitFor(() => screen.getByText('btn_new_user'));
    fireEvent.click(screen.getByText('btn_new_user'));
    expect(screen.getByText('modal_create_title')).toBeInTheDocument();
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
  });
});
