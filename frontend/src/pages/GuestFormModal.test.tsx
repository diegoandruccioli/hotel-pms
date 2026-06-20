import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { createElement, Fragment } from 'react';
import type { ReactNode } from 'react';
import { GuestFormModal } from './GuestFormModal';
import { guestService } from '../services/guestService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, fallback?: string) => fallback ?? key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/guestService', () => ({
  guestService: { createGuest: vi.fn(), updateGuest: vi.fn(), deleteGuest: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) =>
    createElement(Fragment, null, children),
}));

describe('GuestFormModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should have no accessibility violations in add mode', async () => {
    const { container } = render(
      <GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  }, 30000);

  it('shows required-field errors on empty submit and blocks the API call', async () => {
    render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

    expect(await screen.findAllByText('common:err_required')).toHaveLength(2);
    expect(guestService.createGuest).not.toHaveBeenCalled();
  });

  it('shows an invalid-email error and does not submit', async () => {
    render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/label_first_name/i), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText(/label_last_name/i), { target: { value: 'Rossi' } });
    fireEvent.change(screen.getByLabelText(/label_email_hint/i), { target: { value: 'not-an-email' } });

    fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

    expect(await screen.findByText('common:err_invalid_email')).toBeInTheDocument();
    expect(guestService.createGuest).not.toHaveBeenCalled();
  });

  it('submits successfully once email or phone is provided', async () => {
    vi.mocked(guestService.createGuest).mockResolvedValueOnce({} as never);
    const onSaved = vi.fn();
    render(<GuestFormModal onClose={vi.fn()} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText(/label_first_name/i), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText(/label_last_name/i), { target: { value: 'Rossi' } });
    fireEvent.change(screen.getByLabelText(/label_email_hint/i), { target: { value: 'mario@test.com' } });

    fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

    await waitFor(() => expect(guestService.createGuest).toHaveBeenCalledTimes(1));
    expect(onSaved).toHaveBeenCalledTimes(1);
  });

  it('shows the email-or-phone error when both are empty but names are filled', async () => {
    render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/label_first_name/i), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText(/label_last_name/i), { target: { value: 'Rossi' } });

    fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

    expect(await screen.findByText('err_email_or_phone')).toBeInTheDocument();
    expect(guestService.createGuest).not.toHaveBeenCalled();
  });
});
