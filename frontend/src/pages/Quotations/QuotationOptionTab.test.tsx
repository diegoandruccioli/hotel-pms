import { afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import i18n from '../../i18n';
import { QuotationOptionTab } from './QuotationOptionTab';

const UNNAMED = { label: '', selectedRoomIds: [] };
const NAMED = { label: 'Suite deluxe', selectedRoomIds: [] };

const renderTab = (option: typeof UNNAMED, index = 1) => {
  const onRemove = vi.fn();
  render(
    <QuotationOptionTab
      option={option}
      index={index}
      isActive
      canRemove
      total={0}
      onSelect={vi.fn()}
      onRemove={onRemove}
    />,
  );
  return { onRemove };
};

describe('QuotationOptionTab', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('keeps both the tab and the remove button at least 44 px tall (40 px with a mouse)', async () => {
    await i18n.changeLanguage('en');
    renderTab(UNNAMED);

    const tab = screen.getByRole('button', { name: /^Option 2/ });
    expect(tab.className).toContain('min-h-11');
    expect(tab.className).toContain('sm:pointer-fine:min-h-10');
    const remove = screen.getByRole('button', { name: 'Remove Option 2' });
    expect(remove.className).toContain('min-h-11');
    expect(remove.className).toContain('min-w-11');
    expect(remove.className).toContain('sm:pointer-fine:min-h-10');
  });

  it('names the default option and its remove button in English', async () => {
    await i18n.changeLanguage('en');
    const { onRemove } = renderTab(UNNAMED);

    expect(screen.getByRole('button', { name: /^Option 2/ })).toBeInTheDocument();
    const remove = screen.getByRole('button', { name: 'Remove Option 2' });
    fireEvent.click(remove);
    expect(onRemove).toHaveBeenCalledWith(1);
    expect(screen.queryByLabelText(/Rimuovi/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Opzione/)).not.toBeInTheDocument();
  });

  it('names the default option and its remove button in Italian', async () => {
    await i18n.changeLanguage('it');
    renderTab(UNNAMED);

    expect(screen.getByRole('button', { name: /^Opzione 2/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rimuovi Opzione 2' })).toBeInTheDocument();
  });

  it('uses the typed name when the option has one', async () => {
    await i18n.changeLanguage('en');
    renderTab(NAMED);

    expect(screen.getByRole('button', { name: 'Remove Suite deluxe' })).toBeInTheDocument();
  });
});
