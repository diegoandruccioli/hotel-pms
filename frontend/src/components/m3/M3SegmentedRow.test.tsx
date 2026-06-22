import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3SegmentedRow, type M3SegmentOption } from './M3SegmentedRow';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

type Choice = 'a' | 'b' | 'c';

const OPTIONS: M3SegmentOption<Choice>[] = [
  { value: 'a', labelKey: 'opt_a', icon: 'a_icon' },
  { value: 'b', labelKey: 'opt_b', icon: 'b_icon' },
  { value: 'c', labelKey: 'opt_c', icon: 'c_icon' },
];

describe('M3SegmentedRow', () => {
  it('renders all options as radio buttons', () => {
    render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    expect(screen.getAllByRole('radio')).toHaveLength(3);
  });

  it('marks the active option as checked', () => {
    render(<M3SegmentedRow options={OPTIONS} value="b" onChange={vi.fn()} ariaLabel="choices" />);
    expect(screen.getByRole('radio', { name: 'opt_b' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: 'opt_a' })).toHaveAttribute('aria-checked', 'false');
  });

  it('calls onChange with the clicked option value', () => {
    const onChange = vi.fn();
    render(<M3SegmentedRow options={OPTIONS} value="a" onChange={onChange} ariaLabel="choices" />);
    fireEvent.click(screen.getByRole('radio', { name: 'opt_c' }));
    expect(onChange).toHaveBeenCalledWith('c');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
