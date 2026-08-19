/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'vitest-axe';
import { RouteAnnouncer } from './RouteAnnouncer';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <div id="main-content" tabIndex={-1} />
      <RouteAnnouncer />
    </MemoryRouter>,
  );

describe('RouteAnnouncer', () => {
  afterEach(() => {
    document.title = '';
  });

  it('renders a persistent polite live region', () => {
    renderAt('/guests');
    const region = document.querySelector('[aria-live="polite"]');
    expect(region).toBeInTheDocument();
    expect(region).toHaveAttribute('aria-atomic', 'true');
  });

  it('announces the section label for a known route', async () => {
    renderAt('/guests');
    await waitFor(() => {
      expect(screen.getByText('nav_guests')).toBeInTheDocument();
    });
  });

  it('announces the dashboard label at the root path', async () => {
    renderAt('/');
    await waitFor(() => {
      expect(screen.getByText('nav_dashboard')).toBeInTheDocument();
    });
  });

  it('resolves a sub-route to its section label (e.g. a reservation form)', async () => {
    renderAt('/reservations/new');
    await waitFor(() => {
      expect(screen.getByText('nav_reservations')).toBeInTheDocument();
    });
  });

  it('falls back to the dashboard label for an unmapped path', async () => {
    renderAt('/some-unknown-path');
    await waitFor(() => {
      expect(screen.getByText('nav_dashboard')).toBeInTheDocument();
    });
  });

  it('updates document.title on navigation', async () => {
    renderAt('/billing');
    await waitFor(() => {
      expect(document.title).toContain('nav_billing');
    });
  });

  it('moves focus to #main-content', async () => {
    renderAt('/rooms');
    await waitFor(() => {
      expect(document.getElementById('main-content')).toHaveFocus();
    });
  });

  it('has no accessibility violations', async () => {
    const { container } = renderAt('/stays');
    await waitFor(() => expect(screen.getByText('nav_stays')).toBeInTheDocument());
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
