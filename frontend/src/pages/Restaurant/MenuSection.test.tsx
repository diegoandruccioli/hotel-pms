import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { renderWithQuery } from '../../test-utils';
import { MenuSection } from './MenuSection';
import { fbService } from '../../services';
import type { MenuItemResponse } from '../../types';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts?.name ? `${key}:${String(opts.name)}` : key),
    i18n: { language: 'en' },
  }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../services/fbService', () => ({
  fbService: { getMenuItems: vi.fn(), deleteMenuItem: vi.fn() },
}));

const mockAddToast = vi.fn();
vi.mock('../../store/toastStore', () => ({
  useToastStore: () => ({ addToast: mockAddToast }),
}));

vi.mock('./MenuFormModal', () => ({
  MenuFormModal: () => <div role="dialog" aria-label="menu-form" />,
}));

const ITEM: MenuItemResponse = {
  id: 'mi1', name: 'Espresso', category: 'Caffè', price: 2.5, available: true, description: null,
};

describe('MenuSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows the empty state when the menu has no items', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValue([]);
    renderWithQuery(<MenuSection />);
    expect(await screen.findByText('menu_no_items')).toBeInTheDocument();
  });

  it('names the item in the delete confirmation and keeps it when cancelled', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValue([ITEM]);
    renderWithQuery(<MenuSection />);
    await screen.findByText('Espresso');

    fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByText('menu_delete_confirm:Espresso')).toBeInTheDocument();

    fireEvent.click(within(dialog).getByRole('button', { name: 'cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(fbService.deleteMenuItem).not.toHaveBeenCalled();
  });

  it('deletes the item and closes the dialog once confirmed', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValue([ITEM]);
    vi.mocked(fbService.deleteMenuItem).mockResolvedValue(undefined);
    renderWithQuery(<MenuSection />);
    await screen.findByText('Espresso');

    fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'delete' }));

    await waitFor(() => expect(fbService.deleteMenuItem).toHaveBeenCalledWith('mi1'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockAddToast).toHaveBeenCalledWith('menu_delete_success', 'success');
  });

  it('closes the dialog and reports the error when the delete fails', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValue([ITEM]);
    vi.mocked(fbService.deleteMenuItem).mockRejectedValue(new Error('boom'));
    renderWithQuery(<MenuSection />);
    await screen.findByText('Espresso');

    fireEvent.click(screen.getByRole('button', { name: /menu_delete_item Espresso/ }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'delete' }));

    await waitFor(() => expect(mockAddToast).toHaveBeenCalledWith('menu_delete_error', 'error'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Espresso')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    vi.mocked(fbService.getMenuItems).mockResolvedValue([ITEM]);
    const { container } = renderWithQuery(<MenuSection />);
    await screen.findByText('Espresso');
    expect(await axe(container)).toHaveNoViolations();
  });
});
