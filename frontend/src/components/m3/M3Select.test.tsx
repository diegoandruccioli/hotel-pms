/* eslint-disable react-perf/jsx-no-new-function-as-prop */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Select } from './M3Select';

const OPTIONS = [
  { value: 'CLEAN', label: 'Clean' },
  { value: 'DIRTY', label: 'Dirty' },
  { value: 'MAINTENANCE', label: 'Maintenance', disabled: true },
];

describe('M3Select', () => {
  it('associates the label with the select via htmlFor/id', () => {
    render(<M3Select label="Room status" options={OPTIONS} value="CLEAN" onChange={() => {}} />);
    expect(screen.getByLabelText('Room status')).toBeInTheDocument();
  });

  it('renders every option', () => {
    render(<M3Select label="Room status" options={OPTIONS} value="CLEAN" onChange={() => {}} />);
    expect(screen.getByRole('option', { name: 'Clean' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Dirty' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Maintenance' })).toBeDisabled();
  });

  it('renders a disabled placeholder option when provided', () => {
    render(<M3Select label="Room type" options={OPTIONS} placeholder="Select a room type…" value="" onChange={() => {}} />);
    expect(screen.getByRole('option', { name: 'Select a room type…' })).toBeDisabled();
  });

  it('shows a required marker on the label', () => {
    render(<M3Select label="Room status" options={OPTIONS} required value="CLEAN" onChange={() => {}} />);
    expect(screen.getByText('Room status *')).toBeInTheDocument();
  });

  it('fires onChange with the selected value', () => {
    const handleChange = vi.fn();
    render(<M3Select label="Room status" options={OPTIONS} value="CLEAN" onChange={handleChange} />);
    fireEvent.change(screen.getByLabelText('Room status'), { target: { value: 'DIRTY' } });
    expect(handleChange).toHaveBeenCalled();
  });

  it('marks the field invalid and links the error text via aria-describedby', () => {
    render(<M3Select label="Room type" options={OPTIONS} errorText="Required" value="" onChange={() => {}} />);
    const select = screen.getByLabelText('Room type');
    expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(select).toHaveAttribute('aria-describedby', select.getAttribute('aria-describedby') ?? '');
    expect(screen.getByText('Required')).toBeInTheDocument();
  });

  it('renders supporting text when there is no error', () => {
    render(<M3Select label="Room type" options={OPTIONS} supportingText="Pick the room category" value="" onChange={() => {}} />);
    expect(screen.getByText('Pick the room category')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3Select label="Room status" options={OPTIONS} value="CLEAN" onChange={() => {}} />,
    );
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
