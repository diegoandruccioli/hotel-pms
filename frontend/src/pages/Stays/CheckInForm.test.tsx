import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CheckInForm } from './CheckInForm';
import { stayService } from '../../services/stayService';
import userEvent from '@testing-library/user-event';
import type { StayResponse } from '../../types/stay.types';

// Mock translations
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

// Mock services
vi.mock('../../services/stayService');

// ---------------------------------------------------------------------------
// Mock data factory
// ---------------------------------------------------------------------------
const mockStayResponse = (overrides: Partial<StayResponse> = {}): StayResponse => ({
  id: 'stay1',
  reservationId: 'res123',
  guestId: 'g1',
  roomId: 'r1',
  status: 'CHECKED_IN',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  ...overrides,
});

describe('CheckInForm', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  const renderComponent = (expectedGuests = 1) => {
    return render(
      <MemoryRouter initialEntries={[{ pathname: '/stays/checkin/res123', state: { guestId: 'g1', roomId: 'r1', expectedGuests } }]}>
        <Routes>
          <Route path="/stays/checkin/:reservationId" element={<CheckInForm />} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('renders correctly with initial expected guests', () => {
    renderComponent(2);
    expect(screen.getByText('Check-in Alloggiati')).toBeInTheDocument();
    
    // Should render 2 guest cards because expectedGuests is 2
    expect(screen.getByText(/Guest 1/i)).toBeInTheDocument();
    expect(screen.getByText(/Guest 2/i)).toBeInTheDocument();
  });

  it('adds and removes guest cards dynamically', async () => {
    renderComponent(1);
    const user = userEvent.setup();

    // Only 1 guest initially
    expect(screen.getByText(/Guest 1/i)).toBeInTheDocument();
    expect(screen.queryByText(/Guest 2/i)).not.toBeInTheDocument();

    // Add guest
    await user.click(screen.getByRole('button', { name: /add guest/i }));
    
    expect(screen.getByText(/Guest 1/i)).toBeInTheDocument();
    expect(screen.getByText(/Guest 2/i)).toBeInTheDocument();

    // Remove guest 2
    const removeBtns = screen.getAllByRole('button', { name: /remove/i });
    expect(removeBtns).toHaveLength(2); // Since there are 2 guests now, both might have remove button
    await user.click(removeBtns[1]);

    expect(screen.queryByText(/Guest 2/i)).not.toBeInTheDocument();
  });

  it('validates primary guest and submits', async () => {
    vi.mocked(stayService.createStay).mockResolvedValue(mockStayResponse());
    renderComponent(1);
    const user = userEvent.setup();

    // Fill in required fields for Guest 1
    await user.type(screen.getAllByLabelText(/first name/i)[0], 'John');
    await user.type(screen.getAllByLabelText(/last name/i)[0], 'Doe');
    await user.type(screen.getAllByLabelText(/gender/i)[0], 'M');

    // Date input — use fireEvent.change for date inputs
    const dobInput = document.querySelector('input[type="date"]');
    if (dobInput) {
      fireEvent.change(dobInput, { target: { value: '1990-01-01' } });
    }

    await user.type(screen.getAllByLabelText(/place of birth/i)[0], 'Rome');
    await user.type(screen.getAllByLabelText(/citizenship/i)[0], 'IT');
    await user.type(screen.getAllByLabelText(/document type/i)[0], 'PASSPORT');
    await user.type(screen.getAllByLabelText(/document number/i)[0], 'A1234567');
    await user.type(screen.getAllByLabelText(/document place of issue/i)[0], 'Rome');

    // Select Traveller Type (Ospite Singolo is primary default in code, but let's select it explicitly for the test)
    const travellerTypeSelect = screen.getAllByLabelText(/Tipo Alloggiato/i)[0];
    await user.selectOptions(travellerTypeSelect, 'OSPITE_SINGOLO');

    // Guest 1 is primary by default. Let's uncheck it to test validation
    const primaryCheckbox = screen.getByRole('checkbox', { name: /primary guest/i });
    await user.click(primaryCheckbox); // Uncheck
    
    // Try to submit
    const submitBtn = screen.getByRole('button', { name: /complete check-in/i });
    await user.click(submitBtn);

    // Should show error
    expect(screen.getByText('error_primary_guest_required')).toBeInTheDocument();
    expect(stayService.createStay).not.toHaveBeenCalled();

    // Check it back
    await user.click(primaryCheckbox);
    
    // Submit again
    await user.click(submitBtn);

    await waitFor(() => {
      expect(stayService.createStay).toHaveBeenCalledWith(expect.objectContaining({
        reservationId: 'res123',
        guestId: 'g1',
        roomId: 'r1',
        status: 'CHECKED_IN',
        guests: expect.arrayContaining([
          expect.objectContaining({
            firstName: 'John',
            lastName: 'Doe',
            isPrimaryGuest: true,
            travellerType: 'OSPITE_SINGOLO'
          })
        ])
      }));
    });
  });
});
