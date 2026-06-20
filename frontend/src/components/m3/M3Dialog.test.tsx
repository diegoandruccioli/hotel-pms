import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { createElement, Fragment } from 'react';
import type { ReactNode } from 'react';
import { M3Dialog } from './M3Dialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

describe('M3Dialog', () => {
  it('renders nothing when open is false', () => {
    const { container } = render(
      <M3Dialog open={false} title="Test" onClose={vi.fn()}>
        <p>content</p>
      </M3Dialog>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the title and children when open', () => {
    render(
      <M3Dialog open title="My Dialog" onClose={vi.fn()}>
        <p>dialog body</p>
      </M3Dialog>,
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('My Dialog')).toBeInTheDocument();
    expect(screen.getByText('dialog body')).toBeInTheDocument();
  });

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn();
    render(
      <M3Dialog open title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'close' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when the scrim is clicked', () => {
    const onClose = vi.fn();
    const { container } = render(
      <M3Dialog open title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    fireEvent.click(container.querySelector('[aria-hidden="true"]')!);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls onClose when Escape is pressed', () => {
    const onClose = vi.fn();
    render(
      <M3Dialog open title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('does not call onClose on Escape when closed', () => {
    const onClose = vi.fn();
    render(
      <M3Dialog open={false} title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('ignores non-Escape key presses', () => {
    const onClose = vi.fn();
    render(
      <M3Dialog open title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    fireEvent.keyDown(document, { key: 'Enter' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('moves focus to the close button when opened', () => {
    render(
      <M3Dialog open title="My Dialog" onClose={vi.fn()}>
        <p>body</p>
      </M3Dialog>,
    );
    expect(screen.getByRole('button', { name: 'close' })).toHaveFocus();
  });

  it('uses a custom titleId for aria-labelledby when provided', () => {
    render(
      <M3Dialog open title="My Dialog" titleId="custom-title" onClose={vi.fn()}>
        <p>body</p>
      </M3Dialog>,
    );
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-labelledby', 'custom-title');
  });

  it('removes the keydown listener on unmount', () => {
    const onClose = vi.fn();
    const { unmount } = render(
      <M3Dialog open title="My Dialog" onClose={onClose}>
        <p>body</p>
      </M3Dialog>,
    );
    unmount();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(
      <M3Dialog open title="My Dialog" onClose={vi.fn()}>
        <p>body</p>
      </M3Dialog>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
