import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { CheckInForm } from './CheckInForm';
import { stayService } from '../../services/stayService';
import { guestService } from '../../services/guestService';
import userEvent from '@testing-library/user-event';
import type { StayResponse } from '../../types/stay.types';
import type { GuestResponseDTO } from '../../types/guest.types';

const ITALIA_STATO = { codice: '100000100', descrizione: 'ITALIA' };
const FRANCIA_STATO = { codice: '200000100', descrizione: 'FRANCIA' };
const FIANO_COMUNE = { codice: '412058036', descrizione: 'FIANO ROMANO', provincia: 'RM' };
const PASOR_TIPDOC = { codice: 'PASOR', descrizione: 'PASSAPORTO ORDINARIO' };

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (opts && typeof opts === 'object') {
        return Object.entries(opts).reduce(
          (s, [k, v]) => s.replace(`{{${k}}}`, String(v)),
          key
        );
      }
      return key;
    },
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/stayService');
vi.mock('../../services/guestService');

const mockStayResponse = (overrides: Partial<StayResponse> = {}): StayResponse => ({
  id: 'stay1',
  reservationId: 'res123',
  guestId: 'g1',
  roomId: 'r1',
  status: 'CHECKED_IN',
  alloggiatiSent: false,
  alloggiatiSendFailed: false,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  invoiceCreationFailed: false,
  checkoutEmailFailed: false,
  ...overrides,
});

describe('CheckInForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    // Lookup tables return empty arrays (non-blocking; form still renders)
    vi.mocked(stayService.getLookupStati).mockResolvedValue([]);
    vi.mocked(stayService.getLookupTipdoc).mockResolvedValue([]);
    vi.mocked(stayService.getLastCompletedStayForGuest).mockResolvedValue(null);
  });

  const renderComponent = (expectedGuests = 1) =>
    render(
      <MemoryRouter
        initialEntries={[{
          pathname: '/stays/checkin/res123',
          state: { guestId: 'g1', roomId: 'r1', expectedGuests },
        }]}
      >
        <Routes>
          <Route path="/stays/checkin/:reservationId" element={<CheckInForm />} />
        </Routes>
      </MemoryRouter>
    );

  it('renders correctly with initial expected guests', async () => {
    renderComponent(2);
    await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());
    expect(screen.getAllByText('guest_number')).toHaveLength(2);
  });

  it('adds and removes guest cards dynamically', async () => {
    renderComponent(1);
    await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());
    const user = userEvent.setup();

    expect(screen.getAllByText('guest_number')).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'btn_add_guest' }));
    expect(screen.getAllByText('guest_number')).toHaveLength(2);

    const removeBtns = screen.getAllByRole('button', { name: 'btn_remove' });
    expect(removeBtns).toHaveLength(2);
    await user.click(removeBtns[1]);
    expect(screen.getAllByText('guest_number')).toHaveLength(1);
  });

  it('should have no accessibility violations', async () => {
    const { container } = renderComponent(1);
    await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('blocks submit when no primary guest is set', async () => {
    vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
    const { container } = renderComponent(1);
    await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());
    const user = userEvent.setup({ delay: null });

    // Uncheck the primary guest checkbox (first guest is primary by default)
    const primaryCheckbox = screen.getByRole('checkbox', { name: 'label_primary_guest' });
    await user.click(primaryCheckbox);

    // Use fireEvent.submit to bypass HTML5 required validation and test custom logic
    fireEvent.submit(container.querySelector('form')!);

    await waitFor(() => {
      expect(screen.getByText('err_primary_guest_required')).toBeInTheDocument();
    });
    expect(stayService.createStay).not.toHaveBeenCalled();
  });

  it('blocks submit and shows validation error when stato nascita is not selected', async () => {
    vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
    const { container } = renderComponent(1);
    await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

    // Primary guest is set, but _statoDiNascita is empty — Alloggiati validation must block
    fireEvent.submit(container.querySelector('form')!);

    await waitFor(() => {
      expect(screen.getByText('err_stato_nascita_required')).toBeInTheDocument();
    });
    expect(stayService.createStay).not.toHaveBeenCalled();
  });

  describe('Alloggiati validation and full submit flow', () => {
    async function selectStato(label: string, statoLabel: string) {
      const combo = screen.getByLabelText(new RegExp(`^${label}`), { selector: 'input' });
      fireEvent.change(combo, { target: { value: statoLabel.slice(0, 3) } });
      const option = await screen.findByRole('option', { name: new RegExp(statoLabel) });
      fireEvent.mouseDown(option);
    }

    async function selectComune(label: string) {
      const combo = screen.getByLabelText(new RegExp(`^${label}`), { selector: 'input' });
      fireEvent.change(combo, { target: { value: 'FIA' } });
      const option = await screen.findByRole('option', { name: /FIANO ROMANO/ });
      fireEvent.mouseDown(option);
    }

    it('blocks submit when Italian-born but comune di nascita is not selected', async () => {
      vi.mocked(stayService.getLookupStati).mockResolvedValue([ITALIA_STATO]);
      vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
      const { container } = renderComponent(1);
      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

      await selectStato('label_stato_nascita', 'ITALIA');

      fireEvent.submit(container.querySelector('form')!);

      await waitFor(() => {
        expect(screen.getByText('err_comune_nascita_required')).toBeInTheDocument();
      });
      expect(stayService.createStay).not.toHaveBeenCalled();
    });

    it('blocks submit when a document is required but stato rilascio is not selected', async () => {
      vi.mocked(stayService.getLookupStati).mockResolvedValue([FRANCIA_STATO]);
      vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
      const { container } = renderComponent(1);
      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

      // Non-Italian birth — no comune di nascita required, clears that check.
      await selectStato('label_stato_nascita', 'FRANCIA');

      fireEvent.submit(container.querySelector('form')!);

      await waitFor(() => {
        expect(screen.getByText('err_stato_rilascio_required')).toBeInTheDocument();
      });
      expect(stayService.createStay).not.toHaveBeenCalled();
    });

    it('blocks submit when document was issued in Italy but comune rilascio is not selected', async () => {
      vi.mocked(stayService.getLookupStati).mockResolvedValue([FRANCIA_STATO, ITALIA_STATO]);
      vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
      const { container } = renderComponent(1);
      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

      await selectStato('label_stato_nascita', 'FRANCIA');
      await selectStato('label_stato_rilascio_doc', 'ITALIA');

      fireEvent.submit(container.querySelector('form')!);

      await waitFor(() => {
        expect(screen.getByText('err_comune_rilascio_required')).toBeInTheDocument();
      });
      expect(stayService.createStay).not.toHaveBeenCalled();
    });

    it('submits successfully once all required Alloggiati fields are filled, then navigates to /stays', async () => {
      vi.mocked(stayService.getLookupStati).mockResolvedValue([ITALIA_STATO, FRANCIA_STATO]);
      vi.mocked(stayService.getLookupTipdoc).mockResolvedValue([PASOR_TIPDOC]);
      vi.mocked(stayService.searchLookupComuni).mockResolvedValue([FIANO_COMUNE]);
      vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());

      render(
        <MemoryRouter
          initialEntries={[{
            pathname: '/stays/checkin/res123',
            state: { guestId: 'g1', roomId: 'r1', expectedGuests: 1 },
          }]}
        >
          <Routes>
            <Route path="/stays/checkin/:reservationId" element={<CheckInForm />} />
            <Route path="/stays" element={<div>stays_page</div>} />
          </Routes>
        </MemoryRouter>,
      );
      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

      fireEvent.change(screen.getByLabelText('label_first_name'), { target: { value: 'Mario' } });
      fireEvent.change(screen.getByLabelText('label_last_name'), { target: { value: 'Rossi' } });
      fireEvent.change(screen.getByLabelText(/^label_gender/, { selector: 'select' }), { target: { value: '1' } });
      fireEvent.change(screen.getByLabelText('label_date_of_birth'), { target: { value: '1990-01-01' } });
      await selectStato('label_citizenship', 'ITALIA');
      await selectStato('label_stato_nascita', 'ITALIA');
      await selectComune('label_comune_nascita');
      fireEvent.change(screen.getByLabelText(/^label_doc_type/, { selector: 'select' }), { target: { value: 'PASOR' } });
      fireEvent.change(screen.getByLabelText('label_doc_number'), { target: { value: 'AB123456' } });
      await selectStato('label_stato_rilascio_doc', 'ITALIA');
      await selectComune('label_comune_rilascio_doc');

      fireEvent.submit(document.querySelector('form')!);

      await waitFor(() => {
        expect(stayService.createStay).toHaveBeenCalledWith(expect.objectContaining({
          reservationId: 'res123',
          guestId: 'g1',
          roomId: 'r1',
          status: 'CHECKED_IN',
          guests: [expect.objectContaining({
            firstName: 'Mario',
            lastName: 'Rossi',
            placeOfBirth: FIANO_COMUNE.codice,
            documentType: 'PASOR',
            documentNumber: 'AB123456',
            documentPlaceOfIssue: FIANO_COMUNE.codice,
            isPrimaryGuest: true,
          })],
        }));
      });
      await waitFor(() => expect(screen.getByText('stays_page')).toBeInTheDocument());
    }, 15000); // multi-field form fill + several async lookups — tight under --coverage instrumentation overhead

    it('shows the API error detail when the check-in request fails', async () => {
      vi.mocked(stayService.getLookupStati).mockResolvedValue([FRANCIA_STATO]);
      vi.mocked(stayService.createStay).mockRejectedValue({
        response: { data: { detail: 'ROOM_ALREADY_OCCUPIED' } },
      });
      const { container } = renderComponent(1);
      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());

      // Switch the primary guest to FAMILIARE so no document is required, simplifying the path to submit.
      fireEvent.change(screen.getByLabelText(/^label_guest_type/, { selector: 'select' }), { target: { value: 'FAMILIARE' } });
      await selectStato('label_stato_nascita', 'FRANCIA');

      fireEvent.submit(container.querySelector('form')!);

      await waitFor(() => {
        expect(screen.getByText('ROOM_ALREADY_OCCUPIED')).toBeInTheDocument();
      });
    });
  });

  describe('prefill from guest profile', () => {
    const mockProfile = (overrides: Partial<GuestResponseDTO> = {}): GuestResponseDTO => ({
      id: 'g1', firstName: 'Anna', lastName: 'Bianchi', email: 'anna@test.com',
      createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00', active: true,
      identityDocuments: [{
        id: 'doc1', documentType: 'PASSPORT', documentNumber: 'X123', issueDate: '2020-01-01',
        expiryDate: '2030-01-01', createdAt: '2020-01-01T00:00:00', updatedAt: '2020-01-01T00:00:00', active: true,
      }],
      ...overrides,
    });

    it('pre-fills name and document from the guest profile when no prior stay exists', async () => {
      vi.mocked(stayService.getLastCompletedStayForGuest).mockResolvedValue(null);
      vi.mocked(guestService.getGuestById).mockResolvedValue(mockProfile());
      renderComponent(1);

      await waitFor(() => {
        expect(screen.getByText('prefill_banner_profile')).toBeInTheDocument();
      });
      expect(screen.getByLabelText('label_first_name')).toHaveValue('Anna');
      expect(screen.getByLabelText('label_last_name')).toHaveValue('Bianchi');
    });

    it('does not show a prefill banner when the profile lookup fails and there is no prior stay', async () => {
      vi.mocked(stayService.getLastCompletedStayForGuest).mockResolvedValue(null);
      vi.mocked(guestService.getGuestById).mockRejectedValue(new Error('not found'));
      renderComponent(1);

      await waitFor(() => expect(screen.getByText('checkin_title')).toBeInTheDocument());
      expect(screen.queryByText('prefill_banner_profile')).not.toBeInTheDocument();
      expect(screen.queryByText('prefill_banner_stay')).not.toBeInTheDocument();
    });
  });
});
