import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3TableActionLink } from './M3TableActionLink';

describe('M3TableActionLink', () => {
  it('renders children text as a button', () => {
    render(<M3TableActionLink>Edit</M3TableActionLink>);
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('BUG-7: always carries a visible focus ring, regardless of tone', () => {
    render(<M3TableActionLink>Edit</M3TableActionLink>);
    expect(screen.getByRole('button').className).toContain('focus-visible:ring-2');
  });

  it('applies primary tone by default', () => {
    render(<M3TableActionLink>Edit</M3TableActionLink>);
    expect(screen.getByRole('button').className).toContain('text-primary');
  });

  it('applies error tone when requested', () => {
    render(<M3TableActionLink tone="error">Delete</M3TableActionLink>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('text-error');
    expect(btn.className).toContain('focus-visible:ring-error');
  });

  it('fires onClick', () => {
    const onClick = vi.fn();
    render(<M3TableActionLink onClick={onClick}>View</M3TableActionLink>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('is disabled when the disabled prop is true', () => {
    render(<M3TableActionLink disabled>View</M3TableActionLink>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('merges an extra className onto the base classes', () => {
    render(<M3TableActionLink className="ml-3">Delete</M3TableActionLink>);
    expect(screen.getByRole('button').className).toContain('ml-3');
  });

  it('meets the 40x40 minimum touch target size', () => {
    render(<M3TableActionLink>Edit</M3TableActionLink>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('min-h-[40px]');
    expect(btn.className).toContain('min-w-[40px]');
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<M3TableActionLink>Edit</M3TableActionLink>);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
