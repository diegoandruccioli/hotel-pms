import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { OrderFormModal } from './OrderFormModal';
import { fbService } from '../../services/fbService';
import { useToastStore } from '../../store/toastStore';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/fbService', () => ({
  fbService: { getMenuItems: vi.fn(), createOrder: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const MENU_ITEMS = [
  { id: 'item-uuid-1', name: 'Espresso', price: 2.5 },
  { id: 'item-uuid-2', name: 'Cappuccino', price: 3.0 },
];

describe('OrderFormModal', () => {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const mockAddToast = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockReturnValue(mockAddToast);
  });

  it('should load and display menu items', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce(MENU_ITEMS);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => {
      expect(screen.getByText('Espresso')).toBeInTheDocument();
      expect(screen.getByText('Cappuccino')).toBeInTheDocument();
    });
  });

  it('should show empty state when no menu items are available', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce([]);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => {
      expect(screen.getByText('no_menu_available')).toBeInTheDocument();
    });
  });

  it('should call createOrder with correct payload on valid submit', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce(MENU_ITEMS);
    vi.mocked(fbService.createOrder).mockResolvedValueOnce({} as never);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('stay_id_label'), {
      target: { value: 'test-stay-id' },
    });

    fireEvent.click(
      screen.getByRole('button', { name: 'increase_quantity Espresso' }),
    );

    fireEvent.click(screen.getByRole('button', { name: /create_order/ }));

    await waitFor(() => {
      expect(fbService.createOrder).toHaveBeenCalledWith({
        stayId: 'test-stay-id',
        items: [{ menuItemId: 'item-uuid-1', quantity: 1 }],
      });
    });
    expect(onCreated).toHaveBeenCalled();
    expect(onClose).toHaveBeenCalled();
  });

  it('should show error toast when no items are selected', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce(MENU_ITEMS);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('stay_id_label'), {
      target: { value: 'test-stay-id' },
    });

    fireEvent.click(screen.getByRole('button', { name: /create_order/ }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('no_items_selected', 'error');
    });
    expect(fbService.createOrder).not.toHaveBeenCalled();
  });

  it('should show validation error when stay id is empty', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce(MENU_ITEMS);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

    fireEvent.click(
      screen.getByRole('button', { name: 'increase_quantity Espresso' }),
    );

    fireEvent.click(screen.getByRole('button', { name: /create_order/ }));

    await waitFor(() => {
      expect(screen.getByText('err_stay_id_required')).toBeInTheDocument();
    });
    expect(fbService.createOrder).not.toHaveBeenCalled();
  });

  it('should show error toast when createOrder fails', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce(MENU_ITEMS);
    vi.mocked(fbService.createOrder).mockRejectedValueOnce(new Error('Network error'));
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText('stay_id_label'), {
      target: { value: 'test-stay-id' },
    });

    fireEvent.click(
      screen.getByRole('button', { name: 'increase_quantity Espresso' }),
    );

    fireEvent.click(screen.getByRole('button', { name: /create_order/ }));

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith('order_creation_failed', 'error');
    });
  });

  it('should call onClose when cancel button is clicked', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce([]);
    render(<OrderFormModal onClose={onClose} onCreated={onCreated} />);

    await waitFor(() => expect(screen.getByText('no_menu_available')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: /cancel/ }));

    expect(onClose).toHaveBeenCalled();
  });

  it('should have no accessibility violations', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValueOnce([]);
    const { container } = render(
      <OrderFormModal onClose={onClose} onCreated={onCreated} />,
    );
    await waitFor(() => expect(screen.getByText('no_menu_available')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
