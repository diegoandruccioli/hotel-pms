import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Stays } from './Stays';
import { stayService } from '../services/stayService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService', () => ({
  stayService: { getAllStays: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn()
}));

describe('Stays', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(stayService.getAllStays).mockReturnValue(new Promise(() => {}));
    render(<Stays />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render stays on success', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce([
      { id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh', status: 'CHECKED_IN', actualCheckInTime: '2026-03-15T14:00:00' },
    ] as never);

    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('room-123…')).toBeInTheDocument();
    });
  });

  it('should show empty state when no stays', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce([]);
    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('no_active_stays')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(stayService.getAllStays).mockRejectedValueOnce(new Error('Network error'));
    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_stays')).toBeInTheDocument();
    });
  });

  it('should render page title', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce([]);
    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('nav_stays')).toBeInTheDocument();
    });
  });
});
