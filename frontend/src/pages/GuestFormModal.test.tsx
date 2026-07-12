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

const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: typeof mockAddToast }) => unknown)({ addToast: mockAddToast }),
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

  it('shows a toast with the API error detail when createGuest rejects', async () => {
    vi.mocked(guestService.createGuest).mockRejectedValueOnce({
      response: { data: { detail: 'EMAIL_ALREADY_EXISTS' } },
    });
    render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.change(screen.getByLabelText(/label_first_name/i), { target: { value: 'Mario' } });
    fireEvent.change(screen.getByLabelText(/label_last_name/i), { target: { value: 'Rossi' } });
    fireEvent.change(screen.getByLabelText(/label_email_hint/i), { target: { value: 'mario@test.com' } });
    fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('EMAIL_ALREADY_EXISTS', 'error'));
  });

  describe('fiscal section', () => {
    it('is collapsed by default in add mode', () => {
      render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
      expect(screen.queryByLabelText(/label_fiscal_code/i)).not.toBeInTheDocument();
    });

    it('expands and collapses when toggle button is clicked', () => {
      render(<GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />);
      const toggle = screen.getByRole('button', { name: /section_fiscal_data/i });

      fireEvent.click(toggle);
      expect(screen.getByLabelText(/label_fiscal_code/i)).toBeInTheDocument();

      fireEvent.click(toggle);
      expect(screen.queryByLabelText(/label_fiscal_code/i)).not.toBeInTheDocument();
    });

    it('is expanded by default when guest has fiscal data', () => {
      const guestWithFiscal = {
        id: 'g2', firstName: 'Mario', lastName: 'Rossi', email: 'mario@test.com',
        fiscalCode: 'RSSMRA74D22A001Q', active: true,
        createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
      };
      render(<GuestFormModal guest={guestWithFiscal} onClose={vi.fn()} onSaved={vi.fn()} />);
      expect(screen.getByLabelText(/label_fiscal_code/i)).toBeInTheDocument();
      expect((screen.getByLabelText(/label_fiscal_code/i) as HTMLInputElement).value).toBe('RSSMRA74D22A001Q');
    });
  });

  describe('edit mode', () => {
    const EXISTING_GUEST = {
      id: 'g1', firstName: 'Luigi', lastName: 'Verdi', email: 'luigi@test.com',
      phone: '+39 333 1234567', city: 'Roma', country: 'IT',
      active: true, createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
    };

    it('parses an existing phone number into prefix and formatted number', () => {
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={vi.fn()} />);
      expect((screen.getByLabelText(/label_phone_number/i) as HTMLInputElement).value).toBe('333 123 4567');
      expect((screen.getByLabelText(/label_phone_hint/i) as HTMLSelectElement).value).toBe('+39');
    });

    it('calls updateGuest (not createGuest) on submit', async () => {
      vi.mocked(guestService.updateGuest).mockResolvedValueOnce({} as never);
      const onSaved = vi.fn();
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={onSaved} />);

      fireEvent.submit(document.getElementById('guest-form') as HTMLFormElement);

      await waitFor(() => expect(guestService.updateGuest).toHaveBeenCalledWith('g1', expect.objectContaining({
        firstName: 'Luigi', lastName: 'Verdi',
      })));
      expect(guestService.createGuest).not.toHaveBeenCalled();
      expect(onSaved).toHaveBeenCalledTimes(1);
    });

    it('reformats the phone number as the user types and supports changing the prefix', () => {
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={vi.fn()} />);

      const phoneInput = screen.getByLabelText(/label_phone_number/i) as HTMLInputElement;
      fireEvent.change(phoneInput, { target: { value: '3331234' } });
      expect(phoneInput.value).toBe('333 123 4');

      fireEvent.change(screen.getByLabelText(/label_phone_hint/i), { target: { value: '+44' } });
      expect((screen.getByLabelText(/label_phone_hint/i) as HTMLSelectElement).value).toBe('+44');
    });

    it('opens and cancels the delete confirmation panel without deleting', () => {
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={vi.fn()} />);

      fireEvent.click(screen.getByText('delete'));
      expect(screen.getByText('confirm_delete_guest')).toBeInTheDocument();

      fireEvent.click(screen.getByText('cancel'));
      expect(screen.queryByText('confirm_delete_guest')).not.toBeInTheDocument();
      expect(guestService.deleteGuest).not.toHaveBeenCalled();
    });

    it('deletes the guest on confirm and calls onSaved', async () => {
      vi.mocked(guestService.deleteGuest).mockResolvedValueOnce(undefined);
      const onSaved = vi.fn();
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={onSaved} />);

      fireEvent.click(screen.getByText('delete'));
      fireEvent.click(screen.getByText('btn_confirm'));

      await waitFor(() => expect(guestService.deleteGuest).toHaveBeenCalledWith('g1'));
      expect(onSaved).toHaveBeenCalledTimes(1);
    });

    it('shows a toast with the API error detail when deleteGuest rejects', async () => {
      vi.mocked(guestService.deleteGuest).mockRejectedValueOnce({
        response: { data: { detail: 'RESOURCE_IN_USE' } },
      });
      render(<GuestFormModal guest={EXISTING_GUEST} onClose={vi.fn()} onSaved={vi.fn()} />);

      fireEvent.click(screen.getByText('delete'));
      fireEvent.click(screen.getByText('btn_confirm'));

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('RESOURCE_IN_USE', 'error'));
    });
  });
});
