import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Stays } from './Stays';
import { stayService } from '../services/stayService';
import { useAuthStore } from '../store/authStore';

vi.mock('react-i18next', () => {
  const t = (key: string) => key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

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

const mockNavigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
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

  it('should filter stays by room number on search input', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'Jane Smith', status: 'CHECKED_IN' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    const input = screen.getByRole('searchbox');
    fireEvent.change(input, { target: { value: '202' } });

    await waitFor(() => {
      expect(screen.queryByText('101')).not.toBeInTheDocument();
      expect(screen.getByText('202')).toBeInTheDocument();
    }, { timeout: 500 });
  });

  it('should filter stays by status chip', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'g1', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
        { id: 's2', roomId: 'r2', roomNumber: '202', guestId: 'g2', guestDisplayName: 'Jane Smith', status: 'EXPECTED' },
      ],
      totalElements: 2, totalPages: 1, number: 0, size: 20, numberOfElements: 2, first: true, last: true, empty: false,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('101')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'status_expected' }));

    await waitFor(() => {
      expect(screen.queryByText('101')).not.toBeInTheDocument();
      expect(screen.getByText('202')).toBeInTheDocument();
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

  it('should navigate to guests page when guest name is clicked', async () => {
    vi.mocked(stayService.getAllStays).mockResolvedValue({
      content: [
        { id: 's1', roomId: 'r1', roomNumber: '101', guestId: 'guest-uuid-001', guestDisplayName: 'John Doe', status: 'CHECKED_IN' },
      ],
      totalElements: 1, totalPages: 1, number: 0, size: 20, numberOfElements: 1, first: true, last: true, empty: false,
    } as never);
    render(<Stays />);
    await waitFor(() => expect(screen.getByText('John Doe')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'John Doe' }));

    expect(mockNavigate).toHaveBeenCalledWith('/guests?search=John%20Doe');
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
