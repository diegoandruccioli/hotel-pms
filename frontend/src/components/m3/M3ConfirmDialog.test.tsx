import { createElement, Fragment } from 'react';
import type { ComponentProps, ReactNode } from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3ConfirmDialog } from './M3ConfirmDialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

const renderDialog = (props: Partial<ComponentProps<typeof M3ConfirmDialog>> = {}) => {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <M3ConfirmDialog
      title="Delete guest"
      titleId="confirm-delete-guest-dialog"
      message="Are you sure?"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  );
  return { onConfirm, onCancel };
};

describe('M3ConfirmDialog', () => {
  it('renders the title and message in a labelled dialog', () => {
    renderDialog();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-labelledby', 'confirm-delete-guest-dialog');
    expect(screen.getByRole('heading', { name: 'Delete guest' })).toBeInTheDocument();
    expect(screen.getByText('Are you sure?')).toBeInTheDocument();
  });

  it('uses the common cancel and confirm labels by default', () => {
    renderDialog();
    expect(screen.getByRole('button', { name: 'cancel' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'confirm' })).toBeInTheDocument();
  });

  it('uses custom labels when given', () => {
    renderDialog({ confirmLabel: 'Delete', cancelLabel: 'Keep' });
    expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Keep' })).toBeInTheDocument();
  });

  it('calls onConfirm when the confirm button is clicked', () => {
    const { onConfirm, onCancel } = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: 'confirm' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onCancel).not.toHaveBeenCalled();
  });

  it('calls onCancel when the cancel button is clicked', () => {
    const { onConfirm, onCancel } = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: 'cancel' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('calls onCancel when the header close button is clicked', () => {
    const { onCancel } = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it('disables cancel and shows the confirm button as loading while loading', () => {
    renderDialog({ loading: true });
    expect(screen.getByRole('button', { name: 'cancel' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /confirm/ })).toBeDisabled();
  });

  it('renders nothing when closed', () => {
    renderDialog({ open: false });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('accepts a node as the message', () => {
    renderDialog({ message: <span data-testid="rich">Rich message</span> });
    expect(screen.getByTestId('rich')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    renderDialog();
    expect(await axe(screen.getByRole('dialog'))).toHaveNoViolations();
  });
});
