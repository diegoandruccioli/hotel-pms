import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { Restaurant } from './Restaurant';
import { fbService } from '../services/fbService';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/fbService', () => ({
  fbService: { getAllOrders: vi.fn() },
}));

describe('Restaurant', () => {
  beforeEach(() => vi.clearAllMocks());

  it('should show loading spinner initially', () => {
    vi.mocked(fbService.getAllOrders).mockReturnValue(new Promise(() => {}));
    render(<Restaurant />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render orders on success', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([
      { id: 'order-12345678', stayId: 'stay-12345678', orderDate: '2026-03-15', totalAmount: 75, status: 'PENDING' },
    ] as never);

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
});
