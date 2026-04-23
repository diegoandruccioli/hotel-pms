import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Restaurant } from './Restaurant';
import { fbService } from '../services/fbService';

vi.mock('react-i18next', () => {
  const t = (key: string) => key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/fbService', () => ({
  fbService: { getAllOrders: vi.fn(), confirmOrder: vi.fn() },
}));

const PENDING_ORDER = {
  id: 'order-12345678',
  stayId: 'stay-12345678',
  orderDate: '2026-03-15',
  totalAmount: 75,
  status: 'PENDING',
} as never;

const BILLED_ORDER = {
  id: 'billed-12345678',
  stayId: 'stay-12345678',
  orderDate: '2026-03-15',
  totalAmount: 30,
  status: 'BILLED_TO_ROOM',
} as never;

describe('Restaurant', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(fbService.getAllOrders).mockReturnValue(new Promise(() => {}));
    render(<Restaurant />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render orders on success', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);

    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByText('order-12...')).toBeInTheDocument();
    });
  });

  it('should show empty state when no orders', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByText('no_orders')).toBeInTheDocument();
    });
  });

  it('should show error on failure', async () => {
    vi.mocked(fbService.getAllOrders).mockRejectedValueOnce(new Error('Network error'));
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByText('error_loading_orders')).toBeInTheDocument();
    });
  });

  it('should show confirm button for PENDING order', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm_order/ })).toBeInTheDocument();
    });
  });

  it('should not show confirm button for BILLED_TO_ROOM order', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([BILLED_ORDER]);
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByText('billed-1...')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: /confirm_order/ })).not.toBeInTheDocument();
  });

  it('should call confirmOrder and reload orders on confirm click', async () => {
    vi.mocked(fbService.getAllOrders)
      .mockResolvedValueOnce([PENDING_ORDER])
      .mockResolvedValueOnce([]);
    vi.mocked(fbService.confirmOrder).mockResolvedValueOnce(BILLED_ORDER);
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm_order/ })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /confirm_order/ }));

    await waitFor(() => {
      expect(screen.getByText('no_orders')).toBeInTheDocument();
    });

    expect(fbService.confirmOrder).toHaveBeenCalledWith('order-12345678');
  });

  it('should show error when confirmOrder fails', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);
    vi.mocked(fbService.confirmOrder).mockRejectedValueOnce(new Error('Server error'));
    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /confirm_order/ })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: /confirm_order/ }));

    await waitFor(() => {
      expect(screen.getByText('error_loading_orders')).toBeInTheDocument();
    });
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    const { container } = render(<Restaurant />);
    await waitFor(() => expect(screen.getByText('no_orders')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
