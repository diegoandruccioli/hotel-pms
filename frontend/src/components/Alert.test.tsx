import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { Alert } from './Alert';

describe('Alert', () => {
  it('renders its children', () => {
    render(<Alert tone="error">Save failed</Alert>);
    expect(screen.getByText('Save failed')).toBeInTheDocument();
  });

  it('uses role alert for errors and status for everything else', () => {
    const { rerender } = render(<Alert tone="error">x</Alert>);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    rerender(<Alert tone="warning">x</Alert>);
    expect(screen.getByRole('status')).toBeInTheDocument();
    rerender(<Alert tone="info">x</Alert>);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('lets the caller override the role', () => {
    render(<Alert tone="warning" role="alert">x</Alert>);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it.each([
    ['error', 'bg-error-container'],
    ['warning', 'bg-secondary-container'],
    ['success', 'bg-tertiary-container'],
    ['info', 'bg-primary-container'],
  ] as const)('applies the %s tone colors', (tone, bg) => {
    render(<Alert tone={tone} role="status">x</Alert>);
    expect(screen.getByRole('status')).toHaveClass(bg);
  });

  it('shows the tone icon by default and the given icon when overridden', () => {
    const { rerender } = render(<Alert tone="warning">x</Alert>);
    expect(screen.getByText('warning')).toBeInTheDocument();
    rerender(<Alert tone="warning" icon="auto_fix_high">x</Alert>);
    expect(screen.getByText('auto_fix_high')).toBeInTheDocument();
    expect(screen.queryByText('warning')).not.toBeInTheDocument();
  });

  it('renders a title above the children', () => {
    render(<Alert tone="warning" title="Heads up">Details</Alert>);
    expect(screen.getByText('Heads up')).toHaveClass('font-medium');
    expect(screen.getByText('Details')).toBeInTheDocument();
  });

  it('renders the action after the text', () => {
    render(<Alert tone="error" action={<a href="/stays">View all</a>}>x</Alert>);
    expect(screen.getByRole('link', { name: 'View all' })).toBeInTheDocument();
  });

  it('uses tighter spacing when compact', () => {
    render(<Alert tone="warning" compact>x</Alert>);
    expect(screen.getByRole('status')).toHaveClass('px-3', 'text-xs');
  });

  it('appends a custom className', () => {
    render(<Alert tone="error" className="mt-4">x</Alert>);
    expect(screen.getByRole('alert')).toHaveClass('mt-4');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <Alert tone="error" title="Save failed" action={<a href="/x">Retry</a>}>Try again later</Alert>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
