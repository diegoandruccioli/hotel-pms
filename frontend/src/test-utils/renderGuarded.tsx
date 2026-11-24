import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { createMemoryRouter, Link, Outlet, RouterProvider } from 'react-router-dom';
import { UnsavedChangesProvider } from '../components/UnsavedChangesProvider';

const identity = (node: ReactElement) => node;

const guardedRoot = (
  <UnsavedChangesProvider>
    <Link to="/elsewhere">leave page</Link>
    <Outlet />
  </UnsavedChangesProvider>
);

/**
 * Renders a page the way App does: in a data router under `UnsavedChangesProvider`, at `/page`.
 * A "leave page" link goes to `/elsewhere`, to test the unsaved-changes prompt. The test file
 * must mock `focus-trap-react` (jsdom has no tabbable nodes) like the M3 dialog tests do.
 */
export function renderGuarded(
  page: ReactElement,
  wrap: (node: ReactElement) => ReactElement = identity,
  locationState?: unknown,
) {
  const router = createMemoryRouter(
    [
      {
        element: guardedRoot,
        children: [
          { path: '/page', element: page },
          { path: '/elsewhere', element: <p>elsewhere page</p> },
        ],
      },
    ],
    { initialEntries: [{ pathname: '/page', state: locationState }] },
  );
  const result = render(wrap(<RouterProvider router={router} />));
  return { router, ...result };
}
