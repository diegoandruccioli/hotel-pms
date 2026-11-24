import { createElement, Fragment, type ReactNode } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { M3Dialog } from '../components/m3';
import { useDiscardGuard } from './useDiscardGuard';

const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

const SUBJECT = 'The guest details will be lost.';

function Modal({ dirty, onClose }: { dirty: boolean; onClose: () => void }) {
  const { requestClose, discardDialog } = useDiscardGuard(dirty, SUBJECT, onClose);
  return (
    <>
      <M3Dialog open title="Edit guest" onClose={requestClose}>
        <button type="button" onClick={requestClose}>cancel-button</button>
      </M3Dialog>
      {discardDialog}
    </>
  );
}

describe('useDiscardGuard', () => {
  it('closes straight away when nothing was edited', () => {
    const onClose = vi.fn();
    render(<Modal dirty={false} onClose={onClose} />);
    fireEvent.click(screen.getByText('cancel-button'));
    expect(onClose).toHaveBeenCalledOnce();
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it.each([
    ['the Cancel button', () => fireEvent.click(screen.getByText('cancel-button'))],
    ['the header close button', () => fireEvent.click(screen.getByRole('button', { name: 'close' }))],
    ['Escape', () => fireEvent.keyDown(document, { key: 'Escape' })],
  ])('asks before closing an edited form with %s, naming what would be lost', (_label, close) => {
    const onClose = vi.fn();
    render(<Modal dirty onClose={onClose} />);
    close();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByText('unsaved_title')).toBeInTheDocument();
    expect(screen.getByText(SUBJECT)).toBeInTheDocument();
  });

  it('keeps the form open and its data when the user chooses to keep editing', () => {
    const onClose = vi.fn();
    render(<Modal dirty onClose={onClose} />);
    fireEvent.click(screen.getByText('cancel-button'));
    fireEvent.click(screen.getByRole('button', { name: 'unsaved_keep_editing' }));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
    expect(screen.getByText('Edit guest')).toBeInTheDocument();
  });

  it('closes when the user chooses to leave without saving', () => {
    const onClose = vi.fn();
    render(<Modal dirty onClose={onClose} />);
    fireEvent.click(screen.getByText('cancel-button'));
    fireEvent.click(screen.getByRole('button', { name: 'unsaved_leave' }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('Escape on the question closes only the question, not the form', () => {
    const onClose = vi.fn();
    render(<Modal dirty onClose={onClose} />);
    fireEvent.click(screen.getByText('cancel-button'));
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByText('Edit guest')).toBeInTheDocument();
  });
});
