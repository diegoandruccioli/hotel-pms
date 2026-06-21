import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Restaurant } from './Restaurant';
import { fbService } from '../services/fbService';
import type { MenuItemResponse } from '../types/fb.types';

vi.mock('react-i18next', () => {
  const t = (key: string) => key;
  return {
    useTranslation: () => ({ t, i18n: { language: 'en' } }),
    initReactI18next: { type: '3rdParty', init: vi.fn() },
  };
});

vi.mock('../services/fbService', () => ({
  fbService: {
    getAllOrders: vi.fn(), confirmOrder: vi.fn(), getMenuItems: vi.fn(), deleteMenuItem: vi.fn(),
  },
}));

const mockAddToast = vi.fn();
vi.mock('../store/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

let mockRole: string | undefined = undefined;
vi.mock('../store/authStore', () => ({
  useAuthStore: (selector: (s: { user: { role: string | undefined } | null }) => unknown) =>
    selector({ user: mockRole ? { role: mockRole } : null }),
}));

vi.mock('./Restaurant/OrderFormModal', () => ({
  OrderFormModal: ({ onClose }: { onClose: () => void }) => (
    <div role="dialog" aria-label="order-form">
      <button type="button" onClick={onClose}>close-modal</button>
    </div>
  ),
}));

vi.mock('./Restaurant/OrderDetailModal', () => ({
  OrderDetailModal: ({ onClose }: { onClose: () => void }) => (
    <div role="dialog" aria-label="order-detail">
      <button type="button" onClick={onClose}>close-detail</button>
    </div>
  ),
}));

vi.mock('./Restaurant/MenuFormModal', () => ({
  MenuFormModal: ({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) => (
    <div role="dialog" aria-label="menu-form">
      <button type="button" onClick={onClose}>close-menu-form</button>
      <button type="button" onClick={onSaved}>save-menu-form</button>
    </div>
  ),
}));

const PENDING_ORDER = {
  id: 'order-12345678',
  stayId: 'stay-12345678',
  roomNumber: '101',
  orderDate: '2026-03-15',
  totalAmount: 75,
  status: 'PENDING',
} as never;

const BILLED_ORDER = {
  id: 'billed-12345678',
  stayId: 'stay-12345678',
  roomNumber: '205',
  orderDate: '2026-03-15',
  totalAmount: 30,
  status: 'BILLED_TO_ROOM',
} as never;

const MENU_ITEM: MenuItemResponse = {
  id: 'mi1', name: 'Espresso', category: 'Generale', price: 2.5, available: true, description: null,
};

describe('Restaurant', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRole = undefined;
    vi.mocked(fbService.getMenuItems).mockResolvedValue([]);
  });

  it('should show loading spinner initially', () => {
    vi.mocked(fbService.getAllOrders).mockReturnValue(new Promise(() => {}));
    render(<Restaurant />);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should render orders on success', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);

    render(<Restaurant />);

    await waitFor(() => {
      expect(screen.getByText('101')).toBeInTheDocument();
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
      expect(screen.getByText('205')).toBeInTheDocument();
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

  it('should open order detail modal when view button is clicked', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);
    render(<Restaurant />);

    await waitFor(() => expect(screen.getByRole('button', { name: /view order-1/ })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /view order-1/ }));

    expect(screen.getByRole('dialog', { name: 'order-detail' })).toBeInTheDocument();
  });

  it('should close order detail modal when modal requests close', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([PENDING_ORDER]);
    render(<Restaurant />);

    await waitFor(() => expect(screen.getByRole('button', { name: /view order-1/ })).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /view order-1/ }));
    expect(screen.getByRole('dialog', { name: 'order-detail' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'close-detail' }));
    expect(screen.queryByRole('dialog', { name: 'order-detail' })).not.toBeInTheDocument();
  });

  it('should open order form modal when new order button is clicked', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    render(<Restaurant />);

    await waitFor(() => expect(screen.getByText('no_orders')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /new_order/ }));

    expect(screen.getByRole('dialog', { name: 'order-form' })).toBeInTheDocument();
  });

  it('should close order form modal when modal requests close', async () => {
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    render(<Restaurant />);

    await waitFor(() => expect(screen.getByText('no_orders')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /new_order/ }));
    expect(screen.getByRole('dialog', { name: 'order-form' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'close-modal' }));
    expect(screen.queryByRole('dialog', { name: 'order-form' })).not.toBeInTheDocument();
  });

  it('does not render the menu management section for non-admin roles', async () => {
    mockRole = 'RECEPTIONIST';
    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    render(<Restaurant />);
    await waitFor(() => expect(screen.getByText('no_orders')).toBeInTheDocument());
    expect(screen.queryByText('menu_title')).not.toBeInTheDocument();
    expect(fbService.getMenuItems).not.toHaveBeenCalled();
  });

  it('retries loading orders when try_again is clicked', async () => {
    vi.mocked(fbService.getAllOrders).mockRejectedValueOnce(new Error('Network error'));
    render(<Restaurant />);
    await waitFor(() => expect(screen.getByText('error_loading_orders')).toBeInTheDocument());

    vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
    fireEvent.click(screen.getByText('try_again'));
    await waitFor(() => expect(screen.getByText('no_orders')).toBeInTheDocument());
  });

  describe('menu management (ADMIN/OWNER only)', () => {
    beforeEach(() => {
      mockRole = 'ADMIN';
    });

    it('renders the menu section with the empty-state message when there are no items', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('menu_title')).toBeInTheDocument());
      expect(screen.getByText('menu_no_items')).toBeInTheDocument();
    });

    it('renders a menu item row with its name, category, price and availability', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([MENU_ITEM]);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());
      expect(screen.getByText('Generale')).toBeInTheDocument();
      expect(screen.getByText('menu_available_yes')).toBeInTheDocument();
    });

    it('shows menu_available_no for an unavailable item', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([{ ...MENU_ITEM, available: false } as never]);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('menu_available_no')).toBeInTheDocument());
    });

    it('opens the menu form to add a new item, then refreshes the list on save', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('menu_add_item')).toBeInTheDocument());

      fireEvent.click(screen.getByText('menu_add_item'));
      expect(screen.getByRole('dialog', { name: 'menu-form' })).toBeInTheDocument();

      vi.mocked(fbService.getMenuItems).mockResolvedValueOnce([MENU_ITEM]);
      fireEvent.click(screen.getByText('save-menu-form'));

      await waitFor(() => expect(screen.queryByRole('dialog', { name: 'menu-form' })).not.toBeInTheDocument());
      expect(fbService.getMenuItems).toHaveBeenCalled();
    });

    it('opens the menu form to edit an existing item', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([MENU_ITEM]);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /menu_edit_item Espresso/ }));
      expect(screen.getByRole('dialog', { name: 'menu-form' })).toBeInTheDocument();

      fireEvent.click(screen.getByText('close-menu-form'));
      expect(screen.queryByRole('dialog', { name: 'menu-form' })).not.toBeInTheDocument();
    });

    it('deletes a menu item after confirmation and shows a success toast', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([MENU_ITEM]);
      vi.mocked(fbService.deleteMenuItem).mockResolvedValueOnce(undefined);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));

      await waitFor(() => expect(fbService.deleteMenuItem).toHaveBeenCalledWith('mi1'));
      expect(mockAddToast).toHaveBeenCalledWith('menu_delete_success', 'success');
    });

    it('does not delete a menu item when the confirmation dialog is declined', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([MENU_ITEM]);
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));

      expect(fbService.deleteMenuItem).not.toHaveBeenCalled();
    });

    it('shows an error toast when deleting a menu item fails', async () => {
      vi.mocked(fbService.getAllOrders).mockResolvedValueOnce([]);
      vi.mocked(fbService.getMenuItems).mockResolvedValue([MENU_ITEM]);
      vi.mocked(fbService.deleteMenuItem).mockRejectedValueOnce(new Error('boom'));
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      render(<Restaurant />);
      await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));

      await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('menu_delete_error', 'error'));
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
