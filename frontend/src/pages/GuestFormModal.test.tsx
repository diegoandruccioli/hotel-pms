import { render } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { createElement, Fragment } from 'react';
import type { ReactNode } from 'react';
import { GuestFormModal } from './GuestFormModal';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, fallback?: string) => fallback ?? key }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('../services/guestService', () => ({
  guestService: { createGuest: vi.fn(), updateGuest: vi.fn(), deleteGuest: vi.fn() },
}));

vi.mock('../store/toastStore', () => ({
  useToastStore: (selector: unknown) =>
    (selector as (s: { addToast: () => void }) => unknown)({ addToast: vi.fn() }),
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) =>
    createElement(Fragment, null, children),
}));

describe('GuestFormModal', () => {
  it('should have no accessibility violations in add mode', async () => {
    const { container } = render(
      <GuestFormModal onClose={vi.fn()} onSaved={vi.fn()} />
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
