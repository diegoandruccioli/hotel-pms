import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Stays } from './Stays';
import { stayService } from '../services/stayService';
import { useAuthStore } from '../store/authStore';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/stayService', () => ({
  stayService: { getAllStays: vi.fn(), downloadAlloggiatiJson: vi.fn(), downloadAlloggiatiReport: vi.fn() },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: vi.fn(),
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn()
}));

describe('Stays', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: null }) => unknown)({ user: null })
    );
  });

  it('should show loading spinner initially', () => {
    vi.mocked(stayService.getAllStays).mockReturnValue(new Promise(() => {}));
    render(<Stays />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render stays on success', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [{ id: 's1', roomId: 'room-1234-abcd', guestId: 'guest-5678-efgh',
        status: 'CHECKED_IN', actualCheckInTime: '2026-03-15T14:00:00' }],
      totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false,
    } as never);

    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('room-123…')).toBeInTheDocument();
    });
  });

  it('should show empty state when no stays', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
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
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    render(<Stays />);

    await waitFor(() => {
      expect(screen.getByText('nav_stays')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    const { container } = render(<Stays />);
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('should not render JSON export button for RECEPTIONIST', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'RECEPTIONIST' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.queryByText('download_json_export')).not.toBeInTheDocument();
  });

  it('should render JSON export button for ADMIN', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'ADMIN' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.getByText('download_json_export')).toBeInTheDocument();
  });

  it('should render JSON export button for OWNER', async () => {
    vi.mocked(useAuthStore).mockImplementation((selector: unknown) =>
      (selector as (s: { user: { role: string } }) => unknown)({ user: { role: 'OWNER' } })
    );
    vi.mocked(stayService.getAllStays).mockResolvedValueOnce({
      content: [], totalElements: 0, totalPages: 1, number: 0, size: 20,
      numberOfElements: 0, first: true, last: true, empty: true,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('no_active_stays')).toBeInTheDocument());
    expect(screen.getByText('download_json_export')).toBeInTheDocument();
  });
});
