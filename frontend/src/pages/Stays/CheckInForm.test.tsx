import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { axe } from 'vitest-axe';
import { CheckInForm } from './CheckInForm';
import { stayService } from '../../services/stayService';
import userEvent from '@testing-library/user-event';
import type { StayResponse } from '../../types/stay.types';

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
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
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
});
