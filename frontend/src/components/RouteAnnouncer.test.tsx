/* eslint-disable react-perf/jsx-no-new-array-as-prop */
import { useCallback } from 'react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
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

  it('announces a nested route by its own title (e.g. a reservation form)', async () => {
    renderAt('/reservations/new');
    await waitFor(() => {
      expect(screen.getByText('new_reservation')).toBeInTheDocument();
    });
  });

  it('titles a dynamic detail route with its static label, not the page data', async () => {
    renderAt('/quotations/q1');
    await waitFor(() => {
      expect(document.title).toContain('quotation_details');
    });
  });

  it('falls back to the section label for an unmapped sub-route', async () => {
    renderAt('/reservations/groups/g1/unmapped');
    await waitFor(() => {
      expect(screen.getByText('nav_reservation_groups')).toBeInTheDocument();
    });
  });

  it('announces night audit instead of falling back to the dashboard', async () => {
    renderAt('/night-audit');
    await waitFor(() => {
      expect(screen.getByText('nav_night_audit')).toBeInTheDocument();
    });
  });

  it('announces reservation groups, not the parent reservations section', async () => {
    renderAt('/reservations/groups');
    await waitFor(() => {
      expect(screen.getByText('nav_reservation_groups')).toBeInTheDocument();
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

  it('leaves focus alone on the first render so the skip link stays the first Tab stop', async () => {
    renderAt('/rooms');
    await waitFor(() => expect(screen.getByText('nav_rooms')).toBeInTheDocument());
    expect(document.getElementById('main-content')).not.toHaveFocus();
  });

  it('moves focus to #main-content after a client-side navigation', async () => {
    const GoToGuests = () => {
      const navigate = useNavigate();
      const handleClick = useCallback(() => navigate('/guests'), [navigate]);
      return <button onClick={handleClick}>go</button>;
    };
    render(
      <MemoryRouter initialEntries={['/rooms']}>
        <div id="main-content" tabIndex={-1} />
        <GoToGuests />
        <RouteAnnouncer />
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText('nav_rooms')).toBeInTheDocument());
    fireEvent.click(screen.getByText('go'));
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
