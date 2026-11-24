import { createElement, Fragment, useCallback, type ReactNode } from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, Link, Outlet, useNavigate } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import { UnsavedChangesProvider } from '../components/UnsavedChangesProvider';
import { useUnsavedChangesGuard } from './useUnsavedChangesGuard';

const stableT = (key: string) => key;
const stableI18n = { language: 'en' };
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: stableT, i18n: stableI18n }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

vi.mock('focus-trap-react', () => ({
  default: ({ children }: { children: ReactNode }) => createElement(Fragment, null, children),
}));

const SUBJECT = 'Your changes to the hotel profile will be lost.';

function Form({ dirty, subject = SUBJECT }: { dirty: boolean; subject?: string }) {
  const { allowNavigation } = useUnsavedChangesGuard(dirty, subject);
  const navigate = useNavigate();
  const handleSaved = useCallback(() => {
    allowNavigation();
    navigate('/done');
  }, [allowNavigation, navigate]);
  return (
    <div>
      <Link to="/other">go other</Link>
      <Link to="/form?step=2">go step</Link>
      <Link to="/login">go login</Link>
      <button type="button" onClick={handleSaved}>save and leave</button>
    </div>
  );
}

function Root() {
  return (
    <UnsavedChangesProvider>
      <Outlet />
    </UnsavedChangesProvider>
  );
}

function renderApp(dirty: boolean) {
  const router = createMemoryRouter(
    [
      {
        element: <Root />,
        children: [
          { path: '/form', element: <Form dirty={dirty} /> },
          { path: '/other', element: <p>other page</p> },
          { path: '/done', element: <p>done page</p> },
          { path: '/login', element: <p>login page</p> },
        ],
      },
    ],
    { initialEntries: ['/form'] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

describe('useUnsavedChangesGuard', () => {
  it('lets a clean form navigate away without any prompt', () => {
    const router = renderApp(false);
    fireEvent.click(screen.getByText('go other'));
    expect(router.state.location.pathname).toBe('/other');
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it('asks before leaving a dirty form and names what would be lost', () => {
    const router = renderApp(true);
    fireEvent.click(screen.getByText('go other'));
    expect(router.state.location.pathname).toBe('/form');
    expect(screen.getByText('unsaved_title')).toBeInTheDocument();
    expect(screen.getByText(SUBJECT)).toBeInTheDocument();
  });

  it('keeps the user on the form when they choose to keep editing', () => {
    const router = renderApp(true);
    fireEvent.click(screen.getByText('go other'));
    fireEvent.click(screen.getByRole('button', { name: 'unsaved_keep_editing' }));
    expect(router.state.location.pathname).toBe('/form');
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it('completes the navigation when they choose to leave without saving', async () => {
    const router = renderApp(true);
    fireEvent.click(screen.getByText('go other'));
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'unsaved_leave' }));
    });
    expect(router.state.location.pathname).toBe('/other');
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it('does not prompt after allowNavigation (successful save)', async () => {
    const router = renderApp(true);
    await act(async () => {
      fireEvent.click(screen.getByText('save and leave'));
    });
    expect(router.state.location.pathname).toBe('/done');
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it('does not prompt when only the query string changes on the same page', () => {
    const router = renderApp(true);
    fireEvent.click(screen.getByText('go step'));
    expect(router.state.location.pathname).toBe('/form');
    expect(router.state.location.search).toBe('?step=2');
    expect(screen.queryByText('unsaved_title')).not.toBeInTheDocument();
  });

  it('does not prompt when heading to the login page (logout, expired session)', () => {
    const router = renderApp(true);
    fireEvent.click(screen.getByText('go login'));
    expect(router.state.location.pathname).toBe('/login');
  });

  it('blocks closing the tab only while the form is dirty', () => {
    renderApp(true);
    const dirtyEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);
  });

  it('does not block closing the tab for a clean form', () => {
    renderApp(false);
    const cleanEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(cleanEvent);
    expect(cleanEvent.defaultPrevented).toBe(false);
  });

  it('is a no-op outside the provider (tests rendered in a MemoryRouter)', () => {
    function Bare() {
      useUnsavedChangesGuard(true, SUBJECT);
      return <p>bare</p>;
    }
    render(<Bare />);
    expect(screen.getByText('bare')).toBeInTheDocument();
  });
});
