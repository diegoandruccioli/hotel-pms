import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { axe } from 'vitest-axe';
import { M3StatCard } from './M3StatCard';

const DELTA_UP = { percent: 12, label: '+12% vs prev' };
const DELTA_DOWN = { percent: -5, label: '-5% vs prev' };
const DELTA_ZERO = { percent: 0, label: '0% vs prev' };
const TREND_SINGLE = [3];
const TREND_EMPTY: number[] = [];
const TREND_RANGE = [0, 10];
const TREND_THREE = [0, 5, 10];
const TREND_FLAT = [4, 4, 4];
const TREND_PAIR = [1, 2];
const DELTA_LINK = { percent: 1, label: 'up' };
const ROUTER_STATE = { statusFilter: 'CHECKED_IN' };
const DELTA_AXE = { percent: 8, label: '8% vs prev' };
const TREND_NEGATIVE = [-10, 0, 10];
const TREND_NON_FINITE = [1, Number.NaN, 3, Number.POSITIVE_INFINITY];
const TREND_AXE = [1, 3, 2, 5];

const LocationState = () => <output data-testid="state">{JSON.stringify(useLocation().state)}</output>;

const renderCard = (ui: React.ReactElement) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe('M3StatCard', () => {
  it('renders label, value and icon', () => {
    renderCard(<M3StatCard label="Guests" value="12" icon="group" />);
    expect(screen.getByText('Guests')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('group')).toBeInTheDocument();
  });

  it('omits the icon tile when no icon is given', () => {
    const { container } = renderCard(<M3StatCard label="Guests" value="12" />);
    expect(container.querySelectorAll('.material-symbols-outlined')).toHaveLength(0);
  });

  it('uses the info tone for the icon tile by default and honours an explicit tone', () => {
    const { rerender } = renderCard(<M3StatCard label="A" value="1" icon="group" />);
    expect(screen.getByText('group').parentElement?.className).toContain('bg-primary-container');
    rerender(
      <MemoryRouter>
        <M3StatCard label="A" value="1" icon="group" tone="error" />
      </MemoryRouter>,
    );
    expect(screen.getByText('group').parentElement?.className).toContain('bg-error-container');
  });

  describe('delta', () => {
    it('shows an upward delta with icon and text in the success colour', () => {
      renderCard(<M3StatCard label="A" value="1" delta={DELTA_UP} />);
      const delta = screen.getByText('+12% vs prev');
      expect(delta.closest('p')?.className).toContain('text-tertiary');
      expect(screen.getByText('trending_up')).toBeInTheDocument();
    });

    it('shows a downward delta in the error colour', () => {
      renderCard(<M3StatCard label="A" value="1" delta={DELTA_DOWN} />);
      expect(screen.getByText('-5% vs prev').closest('p')?.className).toContain('text-error');
      expect(screen.getByText('trending_down')).toBeInTheDocument();
    });

    it('treats a zero delta as non-negative', () => {
      renderCard(<M3StatCard label="A" value="1" delta={DELTA_ZERO} />);
      expect(screen.getByText('trending_up')).toBeInTheDocument();
    });

    it('renders nothing when there is no delta', () => {
      renderCard(<M3StatCard label="A" value="1" />);
      expect(screen.queryByText('trending_up')).not.toBeInTheDocument();
      expect(screen.queryByText('trending_down')).not.toBeInTheDocument();
    });
  });

  describe('sparkline', () => {
    const getSvg = (container: HTMLElement) => container.querySelector('svg');

    it('is omitted with fewer than two points', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_SINGLE} />);
      expect(getSvg(container)).toBeNull();
    });

    it('is omitted with an empty trend', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_EMPTY} />);
      expect(getSvg(container)).toBeNull();
    });

    it('is decorative and normalises points between min and max', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_RANGE} />);
      const svg = getSvg(container);
      expect(svg).toHaveAttribute('aria-hidden', 'true');
      expect(svg?.querySelector('polyline')).toHaveAttribute('points', '0,22 100,2');
    });

    it('spreads intermediate points evenly', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_THREE} />);
      expect(container.querySelector('polyline')).toHaveAttribute('points', '0,22 50,12 100,2');
    });

    it('normalises negative and mixed-sign values', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_NEGATIVE} />);
      expect(container.querySelector('polyline')).toHaveAttribute('points', '0,22 50,12 100,2');
    });

    it('drops non-finite values and draws nothing if fewer than two points remain', () => {
      const { container, rerender } = renderCard(<M3StatCard label="A" value="1" trend={TREND_NON_FINITE} />);
      expect(container.querySelector('polyline')).toHaveAttribute('points', '0,22 100,2');
      rerender(<MemoryRouter><M3StatCard label="A" value="1" trend={TREND_SINGLE} /></MemoryRouter>);
      expect(container.querySelector('svg')).toBeNull();
    });

    it('drops the text alternative together with the sparkline', () => {
      renderCard(<M3StatCard label="A" value="1" trend={TREND_SINGLE} trendLabel="Alt text" />);
      expect(screen.queryByText('Alt text')).not.toBeInTheDocument();
    });

    it('draws a flat line in the middle when every value is equal', () => {
      const { container } = renderCard(<M3StatCard label="A" value="1" trend={TREND_FLAT} />);
      expect(container.querySelector('polyline')).toHaveAttribute('points', '0,12 50,12 100,12');
    });

    it('exposes the text alternative to screen readers only', () => {
      renderCard(
        <M3StatCard label="A" value="1" trend={TREND_PAIR} trendLabel="Up from 1 to 2 over 2 days" />,
      );
      const alt = screen.getByText('Up from 1 to 2 over 2 days');
      expect(alt.className).toContain('sr-only');
    });
  });

  describe('link', () => {
    it('is a plain block, not a link, without `to`', () => {
      renderCard(<M3StatCard label="Guests" value="12" />);
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
    });

    it('wraps the whole card in a single link with label and value as its name', () => {
      renderCard(<M3StatCard label="Guests" value="12" icon="group" to="/stays" delta={DELTA_LINK} />);
      const links = screen.getAllByRole('link');
      expect(links).toHaveLength(1);
      expect(links[0]).toHaveAttribute('href', '/stays');
      expect(links[0]).toHaveAccessibleName(/Guests/);
      expect(links[0]).toHaveAccessibleName(/12/);
      expect(links[0]).toContainElement(screen.getByText('up'));
    });

    it('keeps the focus ring with offset and merges className on the link', () => {
      renderCard(<M3StatCard label="Guests" value="12" to="/stays" className="custom-x" />);
      const link = screen.getByRole('link');
      expect(link.className).toContain('focus-visible:ring-offset-2');
      expect(link.className).toContain('custom-x');
    });

    it('forwards router state', async () => {
      render(
        <MemoryRouter>
          <M3StatCard label="Guests" value="12" to="/stays" state={ROUTER_STATE} />
          <LocationState />
        </MemoryRouter>,
      );
      await userEvent.click(screen.getByRole('link'));
      expect(screen.getByTestId('state')).toHaveTextContent('{"statusFilter":"CHECKED_IN"}');
    });
  });

  it('merges a custom className on the card', () => {
    const { container } = renderCard(<M3StatCard label="A" value="1" className="custom-x" />);
    expect(container.firstElementChild?.className).toContain('custom-x');
  });

  it('has no accessibility violations (static, with delta and trend)', async () => {
    const { container } = renderCard(
      <M3StatCard
        label="Guests"
        value="12"
        icon="group"
        delta={DELTA_AXE}
        trend={TREND_AXE}
        trendLabel="Rising over 4 days"
      />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no accessibility violations (as a link)', async () => {
    const { container } = renderCard(
      <M3StatCard label="Guests" value="12" icon="group" to="/stays" trend={TREND_PAIR} trendLabel="Rising" />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
