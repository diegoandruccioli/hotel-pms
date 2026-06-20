import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { MenuFormModal } from './MenuFormModal';
import { fbService } from '../../services/fbService';
import { useToastStore } from '../../store/toastStore';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../../services/fbService', () => ({
  fbService: { createMenuItem: vi.fn(), updateMenuItem: vi.fn() },
}));

vi.mock('../../store/toastStore', () => ({
  useToastStore: vi.fn(),
}));

const EXISTING_ITEM = {
  id: 'item-1',
  name: 'Espresso',
  price: 2.5,
  category: 'Bar',
  description: 'Caffè espresso',
  available: true,
};

describe('MenuFormModal', () => {
  const onClose = vi.fn();
  const onSaved = vi.fn();
  const mockAddToast = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useToastStore).mockReturnValue({ addToast: mockAddToast });
  });

  it('renders empty form fields in add mode', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    expect(screen.getByLabelText(/menu_name/)).toHaveValue('');
    expect(screen.getByLabelText(/menu_category/)).toHaveValue('');
  });

  it('pre-fills form fields in edit mode', () => {
    render(<MenuFormModal item={EXISTING_ITEM as never} onClose={onClose} onSaved={onSaved} />);

    expect(screen.getByLabelText(/menu_name/)).toHaveValue('Espresso');
    expect(screen.getByLabelText(/menu_category/)).toHaveValue('Bar');
    expect(screen.getByLabelText(/menu_price/)).toHaveValue(2.5);
  });

  it('shows a validation error when required fields are missing', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.click(screen.getByRole('button', { name: /salva|save/i }));

    expect(screen.getByRole('alert')).toHaveTextContent(/required/i);
    expect(fbService.createMenuItem).not.toHaveBeenCalled();
  });

  it('shows a validation error when price is not greater than zero', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText(/menu_name/), { target: { value: 'Tiramisù' } });
    fireEvent.change(screen.getByLabelText(/menu_category/), { target: { value: 'Dolci' } });
    fireEvent.change(screen.getByLabelText(/menu_price/), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /salva|save/i }));

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(fbService.createMenuItem).not.toHaveBeenCalled();
  });

  it('creates a new menu item on valid submit', async () => {
    vi.mocked(fbService.createMenuItem).mockResolvedValueOnce({} as never);
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText(/menu_name/), { target: { value: 'Tiramisù' } });
    fireEvent.change(screen.getByLabelText(/menu_category/), { target: { value: 'Dolci' } });
    fireEvent.change(screen.getByLabelText(/menu_price/), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: /salva|save/i }));

    await waitFor(() =>
      expect(fbService.createMenuItem).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Tiramisù', category: 'Dolci', price: 6 }),
      ),
    );
    expect(mockAddToast).toHaveBeenCalledWith('menu_save_success', 'success');
    expect(onSaved).toHaveBeenCalled();
  });

  it('updates an existing menu item on valid submit in edit mode', async () => {
    vi.mocked(fbService.updateMenuItem).mockResolvedValueOnce({} as never);
    render(<MenuFormModal item={EXISTING_ITEM as never} onClose={onClose} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText(/menu_price/), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /salva|save/i }));

    await waitFor(() =>
      expect(fbService.updateMenuItem).toHaveBeenCalledWith(
        'item-1',
        expect.objectContaining({ name: 'Espresso', price: 3 }),
      ),
    );
    expect(onSaved).toHaveBeenCalled();
  });

  it('shows an error toast when the save request fails', async () => {
    vi.mocked(fbService.createMenuItem).mockRejectedValueOnce(new Error('network error'));
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.change(screen.getByLabelText(/menu_name/), { target: { value: 'Tiramisù' } });
    fireEvent.change(screen.getByLabelText(/menu_category/), { target: { value: 'Dolci' } });
    fireEvent.change(screen.getByLabelText(/menu_price/), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: /salva|save/i }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('menu_save_error', 'error'));
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('toggles the available checkbox', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeChecked();
    fireEvent.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });

  it('calls onClose when the Escape key is pressed', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.keyDown(screen.getByLabelText(/menu_name/), { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('calls onClose when the cancel button is clicked', () => {
    render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);

    fireEvent.click(screen.getByRole('button', { name: /annulla|cancel/i }));

    expect(onClose).toHaveBeenCalled();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<MenuFormModal onClose={onClose} onSaved={onSaved} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
