import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { createElement, Fragment } from 'react';
import type { ReactNode } from 'react';
import { M3SideSheet } from './M3SideSheet';
import { M3Dialog } from './M3Dialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

describe('M3SideSheet', () => {
  it('renders nothing when open is false', () => {
    const { container } = render(
      <M3SideSheet open={false} title="Detail" onClose={vi.fn()}>
        <p>content</p>
      </M3SideSheet>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders an accessible modal dialog labelled by its title', () => {
    render(
      <M3SideSheet open title="Guest detail" onClose={vi.fn()}>
        <p>sheet body</p>
      </M3SideSheet>,
    );
    const dialog = screen.getByRole('dialog', { name: 'Guest detail' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByRole('heading', { name: 'Guest detail' })).toBeInTheDocument();
    expect(screen.getByText('sheet body')).toBeInTheDocument();
  });

  it('renders the footer when provided and omits it otherwise', () => {
    const { rerender } = render(
      <M3SideSheet open title="Detail" onClose={vi.fn()} footer={<button type="button">edit</button>}>
        <p>body</p>
      </M3SideSheet>,
    );
    expect(screen.getByRole('button', { name: 'edit' })).toBeInTheDocument();

    rerender(
      <M3SideSheet open title="Detail" onClose={vi.fn()}>
        <p>body</p>
      </M3SideSheet>,
    );
    expect(screen.queryByRole('button', { name: 'edit' })).not.toBeInTheDocument();
  });

  it('moves focus to the close button on open', () => {
    render(
      <M3SideSheet open title="Detail" onClose={vi.fn()}>
        <button type="button">inner</button>
      </M3SideSheet>,
    );
    expect(screen.getByRole('button', { name: 'close' })).toHaveFocus();
  });

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(
      <M3SideSheet open title="Detail" onClose={onClose}>
        <p>body</p>
      </M3SideSheet>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when the scrim is clicked', () => {
    const onClose = vi.fn();
    const { container } = render(
      <M3SideSheet open title="Detail" onClose={onClose}>
        <p>body</p>
      </M3SideSheet>,
    );
    fireEvent.click(container.querySelector('[aria-hidden="true"]')!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when Escape is pressed', () => {
    const onClose = vi.fn();
    render(
      <M3SideSheet open title="Detail" onClose={onClose}>
        <p>body</p>
      </M3SideSheet>,
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not react to Escape while closed', () => {
    const onClose = vi.fn();
    render(
      <M3SideSheet open={false} title="Detail" onClose={onClose}>
        <p>body</p>
      </M3SideSheet>,
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('lets only the topmost overlay react to Escape when a dialog opens over the sheet', () => {
    const onCloseSheet = vi.fn();
    const onCloseDialog = vi.fn();
    const { rerender } = render(
      <>
        <M3SideSheet open title="Sheet" onClose={onCloseSheet}>
          <p>sheet body</p>
        </M3SideSheet>
        <M3Dialog open={false} title="Dialog" onClose={onCloseDialog}>
          <p>dialog body</p>
        </M3Dialog>
      </>,
    );
    rerender(
      <>
        <M3SideSheet open title="Sheet" onClose={onCloseSheet}>
          <p>sheet body</p>
        </M3SideSheet>
        <M3Dialog open title="Dialog" onClose={onCloseDialog}>
          <p>dialog body</p>
        </M3Dialog>
      </>,
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onCloseDialog).toHaveBeenCalledTimes(1);
    expect(onCloseSheet).not.toHaveBeenCalled();
  });

  it('gives two sheets distinct title ids', () => {
    render(
      <>
        <M3SideSheet open title="One" onClose={vi.fn()}>
          <p>a</p>
        </M3SideSheet>
        <M3SideSheet open title="Two" onClose={vi.fn()}>
          <p>b</p>
        </M3SideSheet>
      </>,
    );
    const [first, second] = screen.getAllByRole('dialog');
    expect(first.getAttribute('aria-labelledby')).not.toBe(second.getAttribute('aria-labelledby'));
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3SideSheet open title="Detail" onClose={vi.fn()} footer={<button type="button">edit</button>}>
        <p>body</p>
      </M3SideSheet>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
