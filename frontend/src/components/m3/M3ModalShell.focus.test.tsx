import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { afterEach, describe, it, expect, vi } from 'vitest';
import { createElement, Fragment, useCallback, useState } from 'react';
import type { ReactNode } from 'react';
import { M3Dialog } from './M3Dialog';

const t = vi.hoisted(() => (key: string) => key);
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

type ReturnFocus = (opener: HTMLElement) => HTMLElement | false;
const trapOptions = vi.hoisted(() => [] as Array<{ setReturnFocus?: unknown }>);
vi.mock('focus-trap-react', () => ({
  default: ({ children, focusTrapOptions }: { children: ReactNode; focusTrapOptions?: { setReturnFocus?: unknown } }) => {
    trapOptions.push(focusTrapOptions ?? {});
    return createElement(Fragment, null, children);
  },
}));

const returnFocusFor = (opener: HTMLElement, afterClose?: () => void): HTMLElement | false => {
  const { unmount } = render(<M3Dialog open title="Title" onClose={vi.fn()}><p>body</p></M3Dialog>);
  const setReturnFocus = trapOptions.at(-1)?.setReturnFocus as ReturnFocus;
  // focus-trap asks where to return focus once the closing dialog is already out of the DOM
  unmount();
  afterClose?.();
  return setReturnFocus(opener);
};

afterEach(() => {
  trapOptions.length = 0;
  document.body.replaceChildren();
});

describe('M3ModalShell return focus', () => {
  it('returns focus to the opener while it is still in the page', () => {
    const opener = document.createElement('button');
    document.body.appendChild(opener);

    expect(returnFocusFor(opener)).toBe(opener);
  });

  it('falls back to the main landmark when the opener is gone and nothing has focus', () => {
    const main = document.createElement('main');
    main.id = 'main-content';
    document.body.appendChild(main);

    expect(returnFocusFor(document.createElement('button'))).toBe(main);
  });

  it('leaves focus alone when the opener is gone but something else already took it', () => {
    const main = document.createElement('main');
    main.id = 'main-content';
    const other = document.createElement('button');
    document.body.append(main, other);
    expect(returnFocusFor(document.createElement('button'), () => other.focus())).toBe(false);
  });

  it('leaves focus to the dialog underneath when another dialog is still open', () => {
    const main = document.createElement('main');
    main.id = 'main-content';
    const lower = document.createElement('div');
    lower.setAttribute('role', 'dialog');
    lower.setAttribute('aria-modal', 'true');
    document.body.append(main, lower);

    expect(returnFocusFor(document.createElement('button'))).toBe(false);
  });

  it('returns nothing when the opener is gone and the page has no main landmark', () => {
    expect(returnFocusFor(document.createElement('button'))).toBe(false);
  });
});

describe('M3ModalShell focus kept while open', () => {
  const Swapping = () => {
    const [showForm, setShowForm] = useState(false);
    const openForm = useCallback(() => setShowForm(true), []);
    return (
      <M3Dialog open title="Rates" onClose={vi.fn()}>
        {showForm ? (
          <p>form</p>
        ) : (
          <div>
            <button type="button" onClick={openForm}>add</button>
          </div>
        )}
      </M3Dialog>
    );
  };

  it('puts focus back on the close button when swapped content drops it to the body', async () => {
    render(<Swapping />);
    const add = screen.getByRole('button', { name: 'add' });
    add.focus();
    fireEvent.click(add);

    await waitFor(() => expect(screen.getByRole('button', { name: 'close' })).toHaveFocus());
  });

  it('does not move focus when it stays inside the dialog', async () => {
    const Stable = () => {
      const [n, setN] = useState(0);
      const increment = useCallback(() => setN((v) => v + 1), []);
      return (
        <M3Dialog open title="Stable" onClose={vi.fn()}>
          <button type="button" onClick={increment}>count {n}</button>
        </M3Dialog>
      );
    };
    render(<Stable />);
    const button = screen.getByRole('button', { name: /count/ });
    button.focus();
    fireEvent.click(button);

    await screen.findByRole('button', { name: 'count 1' });
    expect(screen.getByRole('button', { name: 'count 1' })).toHaveFocus();
  });
});
