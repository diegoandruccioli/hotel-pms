import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
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

  it('offers a retry button that calls onRetry once', () => {
    const onRetry = vi.fn();
    render(<Alert tone="error" retryLabel="Try again" onRetry={onRetry}>Could not load</Alert>);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('shows no retry button without both onRetry and retryLabel', () => {
    const { rerender } = render(<Alert tone="error" onRetry={vi.fn()}>x</Alert>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    rerender(<Alert tone="error" retryLabel="Try again">x</Alert>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('gives the retry button a 44 px touch target (40 px with a mouse)', () => {
    render(<Alert tone="error" retryLabel="Try again" onRetry={vi.fn()}>x</Alert>);
    const retry = screen.getByRole('button', { name: 'Try again' });
    expect(retry.className).toContain('min-h-11');
    expect(retry.className).toContain('sm:pointer-fine:min-h-10');
  });

  it('has no accessibility violations with a retry button, also when compact', async () => {
    const { container } = render(
      <Alert tone="error" compact title="We couldn't load it" retryLabel="Try again" onRetry={vi.fn()}>
        Check the connection and try again.
      </Alert>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <Alert tone="error" title="Save failed" action={<a href="/x">Retry</a>}>Try again later</Alert>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
