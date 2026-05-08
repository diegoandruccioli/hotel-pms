import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { OrderDetailModal } from './OrderDetailModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

const ORDER = {
  id: 'order-uuid-1234',
  stayId: 'stay-uuid-5678',
  orderDate: '2026-04-28T10:00:00',
  totalAmount: 17.5,
  status: 'PENDING' as const,
  items: [
    { id: 'item-1', itemName: 'Espresso', quantity: 2, unitPrice: 2.5 },
    { id: 'item-2', itemName: 'Club Sandwich', quantity: 1, unitPrice: 12.5 },
  ],
  createdAt: '2026-04-28T10:00:00',
  updatedAt: '2026-04-28T10:00:00',
};

const EMPTY_ORDER = { ...ORDER, items: [] as typeof ORDER.items };

describe('OrderDetailModal', () => {
  const onClose = vi.fn();

  beforeEach(() => vi.clearAllMocks());

  it('should display order summary information', () => {
    render(<OrderDetailModal order={ORDER} onClose={onClose} />);
    expect(screen.getByTitle('order-uuid-1234')).toBeInTheDocument();
    expect(screen.getByTitle('stay-uuid-5678')).toBeInTheDocument();
  });

  it('should display all order items in the table', () => {
    render(<OrderDetailModal order={ORDER} onClose={onClose} />);
    expect(screen.getByText('Espresso')).toBeInTheDocument();
    expect(screen.getByText('Club Sandwich')).toBeInTheDocument();
  });

  it('should show item quantity', () => {
    render(<OrderDetailModal order={ORDER} onClose={onClose} />);
    const cells = screen.getAllByRole('cell');
    const quantities = cells.filter((c) => c.textContent === '2' || c.textContent === '1');
    expect(quantities.length).toBeGreaterThan(0);
  });

  it('should call onClose when close button is clicked', () => {
    render(<OrderDetailModal order={ORDER} onClose={onClose} />);
    const closeButtons = screen.getAllByRole('button', { name: /close/ });
    fireEvent.click(closeButtons[0]);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('should render without items table when items list is empty', () => {
    render(<OrderDetailModal order={EMPTY_ORDER} onClose={onClose} />);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<OrderDetailModal order={ORDER} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('Espresso')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
