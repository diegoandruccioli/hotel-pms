import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { createElement, Fragment, useCallback, useState } from 'react';
import type { ReactNode } from 'react';
import { M3Dialog } from './M3Dialog';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

// jsdom has no layout, so the real trap cannot activate (no tabbable nodes). The stand-in records
// the options M3Dialog hands it, which is what decides whether Escape would deactivate the trap.
const trapOptions = vi.hoisted(() => [] as Array<{ escapeDeactivates?: boolean }>);
vi.mock('focus-trap-react', () => ({
  default: ({ children, focusTrapOptions }: { children: ReactNode; focusTrapOptions?: { escapeDeactivates?: boolean } }) => {
    trapOptions.push(focusTrapOptions ?? {});
    return createElement(Fragment, null, children);
  },
}));

const Stacked = () => {
  const [parentOpen, setParentOpen] = useState(true);
  const [childOpen, setChildOpen] = useState(true);
  const closeParent = useCallback(() => setParentOpen(false), []);
  const closeChild = useCallback(() => setChildOpen(false), []);
  return (
    <>
      <M3Dialog open={parentOpen} title="Parent" titleId="parent-title" onClose={closeParent}>
        <button type="button">parent action</button>
      </M3Dialog>
      <M3Dialog open={childOpen} title="Child" titleId="child-title" onClose={closeChild}>
        <button type="button">child action</button>
      </M3Dialog>
    </>
  );
};

const pressEscape = () => fireEvent.keyDown(document, { key: 'Escape' });

describe('M3Dialog stacked on another M3Dialog', () => {
  it('closes only the top dialog on the first Escape and the one underneath on the second', async () => {
    render(<Stacked />);
    expect(screen.getAllByRole('dialog')).toHaveLength(2);

    pressEscape();
    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1));
    expect(screen.getByRole('heading', { name: 'Parent' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Child' })).not.toBeInTheDocument();

    pressEscape();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('leaves Escape to the dialog stack instead of letting the focus trap deactivate on it', () => {
    render(<Stacked />);

    expect(trapOptions.length).toBeGreaterThan(0);
    trapOptions.forEach((options) => expect(options.escapeDeactivates).toBe(false));
  });

  it('gives each dialog its own accessible name without a shared id', () => {
    const Unnamed = () => (
      <>
        <M3Dialog open title="First" onClose={vi.fn()}><p>a</p></M3Dialog>
        <M3Dialog open title="Second" onClose={vi.fn()}><p>b</p></M3Dialog>
      </>
    );
    render(<Unnamed />);

    expect(screen.getByRole('dialog', { name: 'First' })).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: 'Second' })).toBeInTheDocument();
  });
});
