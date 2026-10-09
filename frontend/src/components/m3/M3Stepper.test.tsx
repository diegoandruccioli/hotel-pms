import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3Stepper } from './M3Stepper';

const steps = [
  { id: 'dates', label: 'Dates' },
  { id: 'rooms', label: 'Rooms' },
  { id: 'guest', label: 'Guest' },
  { id: 'summary', label: 'Summary' },
];

const upToTwo = (i: number) => i <= 2;
const upToOne = (i: number) => i <= 1;
const upToZero = (i: number) => i <= 0;
const selectAll = () => true;

const renderStepper = (current: number, isSelectable: (i: number) => boolean) => {
  const onSelect = vi.fn();
  render(
    <M3Stepper
      steps={steps}
      current={current}
      isSelectable={isSelectable}
      onSelect={onSelect}
      ariaLabel="Reservation steps"
      progressLabel={`Step ${current + 1} of ${steps.length}: ${steps[current].label}`}
    />
  );
  return onSelect;
};

describe('M3Stepper', () => {
  it('renders every step in an ordered list with the given label', () => {
    renderStepper(1, upToOne);
    expect(screen.getByRole('list', { name: 'Reservation steps' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(4);
  });

  it('marks only the current step with aria-current="step"', () => {
    renderStepper(1, upToOne);
    const items = screen.getAllByRole('listitem');
    expect(items[1].querySelector('[aria-current="step"]')).not.toBeNull();
    expect(document.querySelectorAll('[aria-current="step"]')).toHaveLength(1);
  });

  it('shows a check icon for completed steps and the number for the others', () => {
    renderStepper(2, upToTwo);
    const items = screen.getAllByRole('listitem');
    expect(items[0].textContent).toContain('check');
    expect(items[1].textContent).toContain('check');
    expect(items[2].textContent).toContain('3');
    expect(items[3].textContent).toContain('4');
  });

  it('renders selectable steps as buttons and calls onSelect with the index', () => {
    const onSelect = renderStepper(2, upToTwo);
    fireEvent.click(screen.getByRole('button', { name: /Rooms/ }));
    expect(onSelect).toHaveBeenCalledWith(1);
  });

  it('does not render a button for a step that is not selectable', () => {
    const onSelect = renderStepper(0, upToZero);
    expect(screen.queryByRole('button', { name: /Summary/ })).toBeNull();
    expect(screen.getByText('Summary')).toBeInTheDocument();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('lets the caller allow any step (edit mode)', () => {
    const onSelect = renderStepper(0, selectAll);
    fireEvent.click(screen.getByRole('button', { name: /Summary/ }));
    expect(onSelect).toHaveBeenCalledWith(3);
  });

  it('shows the compact progress text used on narrow screens', () => {
    renderStepper(1, upToOne);
    expect(screen.getByText('Step 2 of 4: Rooms')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(
      <M3Stepper
        steps={steps}
        current={1}
        isSelectable={upToOne}
        onSelect={vi.fn()}
        ariaLabel="Reservation steps"
        progressLabel="Step 2 of 4: Rooms"
      />
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
