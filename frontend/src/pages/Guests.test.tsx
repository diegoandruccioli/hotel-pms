import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Guests } from './Guests';
import { guestService } from '../services/guestService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/guestService', () => ({
  guestService: { getAllGuests: vi.fn() },
}));

describe('Guests', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(guestService.getAllGuests).mockReturnValue(new Promise(() => {}));
    render(<Guests />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render guest list on success', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([
      { id: '1', firstName: 'John', lastName: 'Doe', email: 'john@test.com', phone: '123', city: 'Rome', country: 'IT' },
    ] as never);

    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });

  it('should show empty state message when no guests', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('no_guests_found')).toBeInTheDocument();
    });
  });

  it('should show error message on failure', async () => {
    vi.mocked(guestService.getAllGuests).mockRejectedValueOnce(new Error('Network error'));
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_guests')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(guestService.getAllGuests).mockResolvedValueOnce([]);
    render(<Guests />);

    await waitFor(() => {
      expect(screen.getByText('nav_guests')).toBeInTheDocument();
    });
  });
});
