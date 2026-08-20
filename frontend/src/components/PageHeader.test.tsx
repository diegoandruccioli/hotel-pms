import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('renders the title as an h1', () => {
    render(<PageHeader icon="group" title="Guests" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Guests' })).toBeInTheDocument();
  });

  it('renders the subtitle when provided', () => {
    render(<PageHeader icon="group" title="Guests" subtitle="Manage guest profiles" />);
    expect(screen.getByText('Manage guest profiles')).toBeInTheDocument();
  });

  it('omits the subtitle paragraph when not provided', () => {
    render(<PageHeader icon="group" title="Guests" />);
    expect(screen.queryByText('Manage guest profiles')).not.toBeInTheDocument();
  });

  it('renders the actions node when provided', () => {
    render(<PageHeader icon="group" title="Guests" actions={<button type="button">Add guest</button>} />);
    expect(screen.getByRole('button', { name: 'Add guest' })).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <PageHeader icon="group" title="Guests" subtitle="Manage guest profiles" actions={<button type="button">Add guest</button>} />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
