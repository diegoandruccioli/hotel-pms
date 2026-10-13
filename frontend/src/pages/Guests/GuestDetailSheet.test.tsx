import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery as render } from '../../test-utils';
import { GuestDetailSheet } from './GuestDetailSheet';
import { stayService } from '../../services/stayService';
import { billingService } from '../../services/billingService';
import { inventoryService } from '../../services/inventoryService';
import { useAuthStore } from '../../store/authStore';
import type { GuestResponseDTO } from '../../types';

vi.mock('react-i18next', () => ({
  // Surfaces interpolation values so a test can tell which room/count/date a key was given.
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        for (const name of ['name', 'number', 'count']) {
          if (name in opts) return `${key}:${String(opts[name])}`;
        }
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../services/stayService', () => ({ stayService: { getGuestStayHistory: vi.fn() } }));
vi.mock('../../services/billingService', () => ({ billingService: { getGuestInvoiceHistory: vi.fn() } }));
vi.mock('../../services/inventoryService', () => ({ inventoryService: { getAllRooms: vi.fn() } }));
vi.mock('../../store/authStore', () => ({ useAuthStore: vi.fn() }));

const asRole = (role: string) => (selector: unknown) =>
  (selector as (s: { user: { role: string } }) => unknown)({ user: { role } });

const GUEST: GuestResponseDTO = {
  id: 'g-1',
  firstName: 'Mario',
  lastName: 'Rossi',
  email: 'mario@test.com',
  phone: '+39 333 1234567',
  address: 'Via Roma 1',
  cap: '40100',
  comune: 'Bologna',
  provincia: 'BO',
  country: 'Italia',
  dateOfBirth: '1980-05-17',
  fiscalCode: 'RSSMRA80E17A944X',
  identityDocuments: [
    {
      id: 'd-1',
      documentType: 'PASSPORT',
      documentNumber: 'AX1234567',
      issueDate: '2020-01-01',
      expiryDate: '2099-01-01',
      createdAt: '2020-01-01T00:00:00',
      updatedAt: '2020-01-01T00:00:00',
      active: true,
    },
  ],
  createdAt: '2020-01-01T00:00:00',
  updatedAt: '2020-01-01T00:00:00',
  active: true,
};

const stay = (n: number, status = 'CHECKED_OUT') => ({
  stayId: `s-${n}`,
  checkInTime: `2026-0${n}-10T14:00:00`,
  checkOutTime: `2026-0${n}-12T10:00:00`,
  roomId: 'r-1',
  status,
});

const invoice = (n: number) => ({
  invoiceId: `i-${n}`,
  invoiceNumber: `INV-00${n}`,
  issueDate: `2026-0${n}-12`,
  totalAmount: 100 * n,
  status: 'PAID',
});

const renderSheet = (
  guest: GuestResponseDTO = GUEST,
  onEdit = vi.fn(),
  onClose = vi.fn(),
  onNewReservation = vi.fn(),
) => render(
  <GuestDetailSheet guest={guest} onClose={onClose} onEdit={onEdit} onNewReservation={onNewReservation} />,
);

describe('GuestDetailSheet', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuthStore).mockImplementation(asRole('ADMIN'));
    vi.mocked(stayService.getGuestStayHistory).mockResolvedValue([stay(1), stay(2)] as never);
    vi.mocked(billingService.getGuestInvoiceHistory).mockResolvedValue([invoice(1)] as never);
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({
      content: [{ id: 'r-1', roomNumber: '204' }],
    } as never);
  });

  it('shows the name, contacts and fiscal data', async () => {
    renderSheet();
    expect(screen.getByRole('dialog', { name: 'detail_title:Mario Rossi' })).toBeInTheDocument();
    expect(screen.getByText('Mario Rossi')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'mario@test.com' })).toHaveAttribute('href', 'mailto:mario@test.com');
    expect(screen.getByRole('link', { name: '+39 333 1234567' })).toHaveAttribute('href', 'tel:+393331234567');
    expect(screen.getByText(/Via Roma 1/)).toBeInTheDocument();
    expect(screen.getByText(/40100 Bologna \(BO\)/)).toBeInTheDocument();
    expect(screen.getByText('RSSMRA80E17A944X')).toBeInTheDocument();
    await waitFor(() => expect(stayService.getGuestStayHistory).toHaveBeenCalledWith('g-1'));
  });

  it('names the sheet after the guest so a screen reader announces which record is open', () => {
    renderSheet({ ...GUEST, firstName: 'Anna', lastName: 'Bianchi' });

    expect(screen.getByRole('dialog', { name: 'detail_title:Anna Bianchi' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'detail_title:Anna Bianchi' })).toBeInTheDocument();
  });

  it('omits contacts and fiscal rows the guest does not have', () => {
    renderSheet({
      ...GUEST,
      phone: undefined,
      address: undefined,
      cap: undefined,
      comune: undefined,
      provincia: undefined,
      country: undefined,
      dateOfBirth: undefined,
      fiscalCode: undefined,
      identityDocuments: [],
    });
    expect(screen.getByRole('link', { name: 'mario@test.com' })).toBeInTheDocument();
    expect(document.querySelector('a[href^="tel:"]')).toBeNull();
    expect(screen.queryByText('label_birth_date')).not.toBeInTheDocument();
    expect(screen.queryByText('label_fiscal_code')).not.toBeInTheDocument();
    expect(screen.getByText('msg_no_documents')).toBeInTheDocument();
  });

  it('lists the identity documents and flags an expired one', () => {
    renderSheet({
      ...GUEST,
      identityDocuments: [
        { ...GUEST.identityDocuments![0] },
        { ...GUEST.identityDocuments![0], id: 'd-2', documentNumber: 'OLD999', expiryDate: '2001-01-01' },
      ],
    });
    expect(screen.getByText(/AX1234567/)).toBeInTheDocument();
    expect(screen.getByText(/OLD999/)).toBeInTheDocument();
    expect(screen.getAllByText('status_document_expired')).toHaveLength(1);
  });

  it('shows the stay history with the room number and a stays count', async () => {
    renderSheet();
    expect((await screen.findAllByText('label_room:204')).length).toBe(2);
    expect(screen.getByText('stat_stays_count:2')).toBeInTheDocument();
    expect(screen.getAllByText('common:status_checked_out').length).toBe(2);
  });

  it('falls back to a dash when the room is not in the lookup', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValue({ content: [] } as never);
    renderSheet();
    await waitFor(() => expect(inventoryService.getAllRooms).toHaveBeenCalled());
    expect(await screen.findAllByText('label_room:—')).toHaveLength(2);
    expect(screen.queryByText('label_room:204')).not.toBeInTheDocument();
  });

  it('caps the stay history at 5 rows and reports the rest', async () => {
    vi.mocked(stayService.getGuestStayHistory).mockResolvedValue([1, 2, 3, 4, 5, 6, 7].map((n) => stay(n)) as never);
    renderSheet();
    await screen.findAllByText('common:status_checked_out');
    expect(screen.getAllByText('common:status_checked_out')).toHaveLength(5);
    expect(screen.getByText('msg_more_items:2')).toBeInTheDocument();
  });

  it('shows an empty state when there are no stays', async () => {
    vi.mocked(stayService.getGuestStayHistory).mockResolvedValue([]);
    renderSheet();
    expect(await screen.findByText('msg_no_stays')).toBeInTheDocument();
  });

  it('isolates a failing stay history: the error shows and the rest stays visible', async () => {
    vi.mocked(stayService.getGuestStayHistory).mockRejectedValue(new Error('boom'));
    renderSheet();
    expect(await screen.findByText('err_history_load')).toBeInTheDocument();
    expect(screen.getByText('Mario Rossi')).toBeInTheDocument();
    expect(await screen.findByText('INV-001')).toBeInTheDocument();
  });

  it('shows the invoice history with formatted totals to ADMIN', async () => {
    renderSheet();
    expect(await screen.findByText('INV-001')).toBeInTheDocument();
    expect(screen.getByText(/€100.00/)).toBeInTheDocument();
    expect(screen.getByText('common:invoice_status_PAID')).toBeInTheDocument();
  });

  it('shows the invoice history to OWNER too', async () => {
    vi.mocked(useAuthStore).mockImplementation(asRole('OWNER'));
    renderSheet();
    expect(await screen.findByText('INV-001')).toBeInTheDocument();
  });

  it('isolates a failing invoice history', async () => {
    vi.mocked(billingService.getGuestInvoiceHistory).mockRejectedValue(new Error('boom'));
    renderSheet();
    expect(await screen.findByText('err_history_load')).toBeInTheDocument();
    expect(await screen.findAllByText('label_room:204')).toHaveLength(2);
  });

  it('builds a mailto link that cannot add headers through the local part', () => {
    renderSheet({ ...GUEST, email: 'x?cc=a@evil.com&body=hi@test.com' });
    expect(screen.getByRole('link', { name: 'x?cc=a@evil.com&body=hi@test.com' })).toHaveAttribute(
      'href',
      'mailto:x%3Fcc%3Da%40evil.com%26body%3Dhi@test.com',
    );
  });

  it('shows an empty state when there are no invoices', async () => {
    vi.mocked(billingService.getGuestInvoiceHistory).mockResolvedValue([]);
    renderSheet();
    expect(await screen.findByText('msg_no_invoices')).toBeInTheDocument();
  });

  it('hides the invoice section and never calls the endpoint for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation(asRole('RECEPTIONIST'));
    renderSheet();
    await screen.findAllByText('common:status_checked_out');
    expect(screen.queryByText('section_invoice_history')).not.toBeInTheDocument();
    expect(billingService.getGuestInvoiceHistory).not.toHaveBeenCalled();
  });

  it('calls onEdit with the guest from the footer button', () => {
    const onEdit = vi.fn();
    renderSheet(GUEST, onEdit);
    fireEvent.click(screen.getByRole('button', { name: 'common:edit' }));
    expect(onEdit).toHaveBeenCalledWith(GUEST);
  });

  it('calls onNewReservation with the guest from the footer button', () => {
    const onNewReservation = vi.fn();
    renderSheet(GUEST, vi.fn(), vi.fn(), onNewReservation);
    fireEvent.click(screen.getByRole('button', { name: 'common:new_reservation' }));
    expect(onNewReservation).toHaveBeenCalledWith(GUEST);
  });

  it('calls onClose from the close button', () => {
    const onClose = vi.fn();
    renderSheet(GUEST, vi.fn(), onClose);
    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('escapes guest-supplied text instead of rendering HTML', () => {
    renderSheet({ ...GUEST, address: '<img src=x onerror=alert(1)>' });
    expect(screen.getByText(/<img src=x onerror=alert\(1\)>/)).toBeInTheDocument();
    expect(document.querySelector('img')).toBeNull();
  });

  it('has no accessibility violations', async () => {
    const { container } = renderSheet();
    await screen.findByText('INV-001');
    expect(await axe(container)).toHaveNoViolations();
  });
});
