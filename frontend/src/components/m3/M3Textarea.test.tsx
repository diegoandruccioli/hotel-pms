/* eslint-disable react-perf/jsx-no-new-function-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Textarea } from './M3Textarea';

describe('M3Textarea', () => {
  it('associates the label with the textarea', () => {
    render(<M3Textarea label="Description" value="" onChange={() => {}} />);
    expect(screen.getByLabelText('Description')).toBeInTheDocument();
  });

  it('defaults to 3 rows', () => {
    render(<M3Textarea label="Description" value="" onChange={() => {}} />);
    expect(screen.getByLabelText('Description')).toHaveAttribute('rows', '3');
  });

  it('shows a required marker on the label', () => {
    render(<M3Textarea label="Description" required value="" onChange={() => {}} />);
    expect(screen.getByText('Description *')).toBeInTheDocument();
  });

  it('fires onChange', () => {
    const handleChange = vi.fn();
    render(<M3Textarea label="Description" value="" onChange={handleChange} />);
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Grilled salmon' } });
    expect(handleChange).toHaveBeenCalled();
  });

  it('marks the field invalid when errorText is set', () => {
    render(<M3Textarea label="Description" errorText="Too long" value="" onChange={() => {}} />);
    expect(screen.getByLabelText('Description')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Too long')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<M3Textarea label="Description" value="" onChange={() => {}} />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
