import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { CreateUserModal } from './CreateUserModal';
import { userService } from '../../services';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/userService', () => ({
  userService: { createUser: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

describe('CreateUserModal', () => {
  const onClose = vi.fn();
  const onCreated = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('renders the create form', () => {
    render(<CreateUserModal onClose={onClose} onCreated={onCreated} />);
    expect(screen.getByLabelText('label_username')).toBeInTheDocument();
    expect(screen.getByLabelText('label_email')).toBeInTheDocument();
    expect(screen.getByLabelText('label_password')).toBeInTheDocument();
  });

  it('calls onClose when cancel is clicked', () => {
    render(<CreateUserModal onClose={onClose} onCreated={onCreated} />);
    fireEvent.click(screen.getByText('btn_cancel'));
    expect(onClose).toHaveBeenCalled();
  });

  it('calls onCreated with the created user on success', async () => {
    const created = { id: 'u1', username: 'carol', email: 'carol@hotel.com', role: 'OWNER', active: true, mustChangePassword: false, createdAt: '' };
    vi.mocked(userService.createUser).mockResolvedValue(created as never);
    render(<CreateUserModal onClose={onClose} onCreated={onCreated} />);

    fireEvent.change(screen.getByLabelText('label_username'), { target: { value: 'carol' } });
    fireEvent.change(screen.getByLabelText('label_email'), { target: { value: 'carol@hotel.com' } });
    fireEvent.change(screen.getByLabelText('label_password'), { target: { value: 'Secret123!' } });
    fireEvent.click(screen.getByText('btn_create'));

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));
  });

  it('passes axe accessibility check', async () => {
    const { container } = render(<CreateUserModal onClose={onClose} onCreated={onCreated} />);
    expect(await axe(container)).toHaveNoViolations();
  }, 30000);
});
