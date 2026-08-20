/* eslint-disable react-perf/jsx-no-new-function-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Switch } from './M3Switch';

describe('M3Switch', () => {
  it('renders a switch role reflecting the checked state', () => {
    render(<M3Switch checked label="High contrast" onChange={() => {}} />);
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
  });

  it('calls onChange with the flipped value when clicked', () => {
    const handleChange = vi.fn();
    render(<M3Switch checked={false} label="High contrast" onChange={handleChange} />);
    fireEvent.click(screen.getByRole('switch'));
    expect(handleChange).toHaveBeenCalledWith(true);
  });

  it('does not call onChange when disabled', () => {
    const handleChange = vi.fn();
    render(<M3Switch checked={false} label="High contrast" onChange={handleChange} disabled />);
    fireEvent.click(screen.getByRole('switch'));
    expect(handleChange).not.toHaveBeenCalled();
  });

  it('renders the description text when provided', () => {
    render(<M3Switch checked={false} label="High contrast" description="Boosts text contrast" onChange={() => {}} />);
    expect(screen.getByText('Boosts text contrast')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3Switch checked label="High contrast" description="Boosts text contrast" icon="contrast" onChange={() => {}} />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
