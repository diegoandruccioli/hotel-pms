import { screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { QuotationSendDialog } from './QuotationSendDialog';
import { guestService } from '../../services/guestService';
import { renderWithQuery } from '../../test-utils';
import type { QuotationResponse } from '../../types';

// One stable `t` for every render: a new function each time would re-run effects that depend on it.
const i18nMock = vi.hoisted(() => {
  const t = (key: string, opts?: Record<string, unknown>) => (opts?.name ? `${key}:${opts.name}` : key);
  return { t };
});
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: i18nMock.t, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/guestService', () => ({
  guestService: { getGuestById: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const GUEST_QUOTATION = {
  id: 'q1',
  guestId: 'g1',
  guestFullName: 'Mario Rossi',
  prospectEmail: null,
  status: 'DRAFT',
} as unknown as QuotationResponse;

const PROSPECT_QUOTATION = {
  ...GUEST_QUOTATION,
  guestId: null,
  guestFullName: 'Anna Bianchi',
  prospectEmail: 'anna.bianchi@example.com',
} as unknown as QuotationResponse;

const LocationProbe = () => <output data-testid="state">{JSON.stringify(useLocation().state)}</output>;

const onConfirm = vi.fn();
const onCancel = vi.fn();

const renderDialog = (quotation: QuotationResponse, loading = false) =>
  renderWithQuery(
    <MemoryRouter>
      <QuotationSendDialog quotation={quotation} onConfirm={onConfirm} onCancel={onCancel} loading={loading} />
      <LocationProbe />
    </MemoryRouter>,
  );

describe('QuotationSendDialog', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('shows a prospect\'s own address without looking anything up', async () => {
    renderDialog(PROSPECT_QUOTATION);

    expect(await screen.findByText('anna.bianchi@example.com')).toBeInTheDocument();
    expect(screen.getByText('Anna Bianchi')).toBeInTheDocument();
    expect(guestService.getGuestById).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'send_dialog_confirm' })).toBeEnabled();
  });

  it('looks up a registered guest\'s address and confirms with it', async () => {
    vi.mocked(guestService.getGuestById).mockResolvedValue({ id: 'g1', email: 'mario.rossi@example.com' } as never);
    renderDialog(GUEST_QUOTATION);

    // Until the address is known the dialog cannot be confirmed.
    expect(screen.getByText('send_dialog_loading')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'send_dialog_confirm' })).toBeDisabled();

    expect(await screen.findByText('mario.rossi@example.com')).toBeInTheDocument();
    expect(guestService.getGuestById).toHaveBeenCalledWith('g1');
    expect(screen.getByText('send_dialog_cooldown')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'send_dialog_confirm' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('blocks a guest with no email and links to the guest record', async () => {
    vi.mocked(guestService.getGuestById).mockResolvedValue({ id: 'g1', email: '  ' } as never);
    renderDialog(GUEST_QUOTATION);

    expect(await screen.findByText('send_dialog_no_email:Mario Rossi')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'send_dialog_confirm' })).toBeDisabled();

    const link = screen.getByRole('link', { name: 'send_dialog_open_guest' });
    expect(link).toHaveAttribute('href', '/guests');
    fireEvent.click(link);
    await waitFor(() => expect(screen.getByTestId('state')).toHaveTextContent('{"guestId":"g1"}'));
  });

  it('does not offer to send when the guest cannot be read', async () => {
    vi.mocked(guestService.getGuestById).mockRejectedValue(new Error('boom'));
    renderDialog(GUEST_QUOTATION);

    expect(await screen.findByText('send_dialog_guest_unreadable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'send_dialog_confirm' })).toBeDisabled();
    expect(screen.queryByRole('link', { name: 'send_dialog_open_guest' })).not.toBeInTheDocument();
  });

  it('retries the guest lookup and enables the send once the address is read', async () => {
    vi.mocked(guestService.getGuestById)
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValue({ id: 'g1', email: 'mario.rossi@example.com' } as never);
    renderDialog(GUEST_QUOTATION);

    fireEvent.click(await screen.findByRole('button', { name: 'common:try_again' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'send_dialog_confirm' })).toBeEnabled());
    expect(guestService.getGuestById).toHaveBeenCalledTimes(2);
    expect(screen.queryByText('send_dialog_guest_unreadable')).not.toBeInTheDocument();
  });

  it('uses the resend wording for a quotation that was already sent', async () => {
    renderDialog({ ...PROSPECT_QUOTATION, status: 'SENT' } as QuotationResponse);

    expect(await screen.findByText('send_dialog_title_resend')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'send_dialog_confirm_resend' })).toBeInTheDocument();
  });

  it('cancel calls onCancel and is disabled while the send runs', async () => {
    const { unmount } = renderDialog(PROSPECT_QUOTATION);
    fireEvent.click(await screen.findByRole('button', { name: 'common:cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    unmount();

    renderDialog(PROSPECT_QUOTATION, true);
    expect(await screen.findByRole('button', { name: 'common:cancel' })).toBeDisabled();
  });

  it('passes axe accessibility check', async () => {
    renderDialog(PROSPECT_QUOTATION);
    await screen.findByText('anna.bianchi@example.com');
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
