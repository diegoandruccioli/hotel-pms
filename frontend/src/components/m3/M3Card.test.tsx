import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { M3Card } from './M3Card';

describe('M3Card', () => {
  it('should render children', () => {
    render(<M3Card>Card content</M3Card>);
    expect(screen.getByText('Card content')).toBeInTheDocument();
  });

  it('should apply elevated variant by default', () => {
    render(<M3Card>Test</M3Card>);
    const el = screen.getByText('Test');
    expect(el.className).toContain('shadow-elevation-1');
  });

  it('should apply filled variant classes', () => {
    render(<M3Card variant="filled">Filled</M3Card>);
    const el = screen.getByText('Filled');
    expect(el.className).toContain('bg-surface-container-highest');
  });

  it('should apply outlined variant classes', () => {
    render(<M3Card variant="outlined">Outlined</M3Card>);
    const el = screen.getByText('Outlined');
    expect(el.className).toContain('border');
  });

  it('should apply glass variant classes', () => {
    render(<M3Card variant="glass">Glass</M3Card>);
    const el = screen.getByText('Glass');
    expect(el.className).toContain('glass-surface');
  });

  it('should pass additional className', () => {
    render(<M3Card className="p-4">Styled</M3Card>);
    const el = screen.getByText('Styled');
    expect(el.className).toContain('p-4');
  });
});
