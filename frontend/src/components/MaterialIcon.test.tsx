import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MaterialIcon } from './MaterialIcon';

describe('MaterialIcon', () => {
  it('should render the icon name as text content', () => {
    render(<MaterialIcon name="dashboard" />);
    expect(screen.getByText('dashboard')).toBeInTheDocument();
  });

  it('should apply material-symbols-outlined class', () => {
    render(<MaterialIcon name="hotel" />);
    const el = screen.getByText('hotel');
    expect(el.className).toContain('material-symbols-outlined');
  });

  it('should apply filled class when filled is true', () => {
    render(<MaterialIcon name="star" filled />);
    const el = screen.getByText('star');
    expect(el.className).toContain('filled');
  });

  it('should set aria-hidden when no label', () => {
    render(<MaterialIcon name="close" />);
    const el = screen.getByText('close');
    expect(el).toHaveAttribute('aria-hidden', 'true');
  });

  it('should set aria-label and role=img when label is provided', () => {
    render(<MaterialIcon name="close" label="Close dialog" />);
    const el = screen.getByLabelText('Close dialog');
    expect(el).toHaveAttribute('role', 'img');
  });

  it('should apply custom size via inline style', () => {
    render(<MaterialIcon name="check" size={32} />);
    const el = screen.getByText('check');
    expect(el.style.fontSize).toBe('32px');
    expect(el.style.width).toBe('32px');
    expect(el.style.height).toBe('32px');
  });

  it('should apply additional className', () => {
    render(<MaterialIcon name="info" className="text-primary" />);
    const el = screen.getByText('info');
    expect(el.className).toContain('text-primary');
  });
});
