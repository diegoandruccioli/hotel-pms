import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3SegmentedRow, type M3SegmentOption } from './M3SegmentedRow';

const useTranslationMock = vi.hoisted(() => vi.fn());

vi.mock('react-i18next', () => ({
  useTranslation: useTranslationMock,
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

beforeEach(() => {
  useTranslationMock.mockReset();
  useTranslationMock.mockImplementation(() => ({ t: (key: string) => key, i18n: { language: 'en' } }));
});

type Choice = 'a' | 'b' | 'c';

const OPTIONS: M3SegmentOption<Choice>[] = [
  { value: 'a', labelKey: 'opt_a', icon: 'a_icon' },
  { value: 'b', labelKey: 'opt_b', icon: 'b_icon' },
  { value: 'c', labelKey: 'opt_c', icon: 'c_icon' },
];

const PLAIN_OPTIONS: M3SegmentOption<Choice>[] = [
  { value: 'a', labelKey: 'opt_a' },
  { value: 'b', labelKey: 'opt_b' },
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

  it('translates labels from the settings namespace by default', () => {
    render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    expect(useTranslationMock).toHaveBeenCalledWith('settings');
  });

  it('translates labels from a custom namespace', () => {
    render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" ns="common" />);
    expect(useTranslationMock).toHaveBeenCalledWith('common');
    expect(useTranslationMock).not.toHaveBeenCalledWith('settings');
  });

  it('shows the icon of unselected options and a check on the selected one', () => {
    render(<M3SegmentedRow options={OPTIONS} value="b" onChange={vi.fn()} ariaLabel="choices" />);
    expect(screen.getByRole('radio', { name: 'opt_b' })).toHaveTextContent('check');
    expect(screen.getByRole('radio', { name: 'opt_b' })).not.toHaveTextContent('b_icon');
    expect(screen.getByRole('radio', { name: 'opt_a' })).toHaveTextContent('a_icon');
    expect(screen.getByRole('radio', { name: 'opt_c' })).toHaveTextContent('c_icon');
  });

  it('renders no icon for unselected options without one', () => {
    render(<M3SegmentedRow options={PLAIN_OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    expect(screen.getByRole('radio', { name: 'opt_b' })).toHaveTextContent(/^opt_b$/);
  });

  it('merges a custom className on the radiogroup', () => {
    render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" className="w-max" />);
    expect(screen.getByRole('radiogroup', { name: 'choices' }).className).toContain('w-max');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
