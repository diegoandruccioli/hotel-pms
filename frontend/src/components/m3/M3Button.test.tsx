import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { M3Button } from './M3Button';

describe('M3Button', () => {
  it('should render children text', () => {
    render(<M3Button>Click me</M3Button>);
    expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
  });

  it('should apply filled variant by default', () => {
    render(<M3Button>Test</M3Button>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('bg-primary');
  });

  it('should apply tonal variant classes', () => {
    render(<M3Button variant="tonal">Tonal</M3Button>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('bg-secondary-container');
  });

  it('should apply outlined variant classes', () => {
    render(<M3Button variant="outlined">Outlined</M3Button>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('border');
  });

  it('should be disabled when disabled prop is true', () => {
    render(<M3Button disabled>Disabled</M3Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should be disabled when loading', () => {
    render(<M3Button loading>Loading</M3Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('should show spinner icon when loading', () => {
    render(<M3Button loading>Loading</M3Button>);
    expect(screen.getByText('progress_activity')).toBeInTheDocument();
  });

  it('should show icon when provided', () => {
    render(<M3Button icon="add">Add</M3Button>);
    expect(screen.getByText('add')).toBeInTheDocument();
  });
});
