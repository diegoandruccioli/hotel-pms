import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

const Controlled = ({ initial = 'a' as Choice }: { initial?: Choice }) => {
  const [value, setValue] = useState<Choice>(initial);
  return (
    <>
      <M3SegmentedRow options={OPTIONS} value={value} onChange={setValue} ariaLabel="choices" />
      <button type="button">after</button>
    </>
  );
};

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

  describe('keyboard navigation', () => {
    const radio = (name: string) => screen.getByRole('radio', { name });

    it('makes only the selected option a tab stop', () => {
      render(<M3SegmentedRow options={OPTIONS} value="b" onChange={vi.fn()} ariaLabel="choices" />);
      expect(radio('opt_a')).toHaveAttribute('tabindex', '-1');
      expect(radio('opt_b')).toHaveAttribute('tabindex', '0');
      expect(radio('opt_c')).toHaveAttribute('tabindex', '-1');
    });

    it('keeps the first option reachable when the value matches no option', () => {
      render(<M3SegmentedRow options={OPTIONS} value={'x' as Choice} onChange={vi.fn()} ariaLabel="choices" />);
      expect(radio('opt_a')).toHaveAttribute('tabindex', '0');
      expect(radio('opt_b')).toHaveAttribute('tabindex', '-1');
    });

    it('enters on the selected option and leaves the group with the next Tab', async () => {
      const user = userEvent.setup();
      render(<Controlled initial="b" />);
      await user.tab();
      expect(radio('opt_b')).toHaveFocus();
      await user.tab();
      expect(screen.getByRole('button', { name: 'after' })).toHaveFocus();
    });

    it('moves focus and selection with ArrowRight and ArrowDown', async () => {
      const user = userEvent.setup();
      render(<Controlled />);
      await user.tab();
      await user.keyboard('{ArrowRight}');
      expect(radio('opt_b')).toHaveFocus();
      expect(radio('opt_b')).toHaveAttribute('aria-checked', 'true');
      await user.keyboard('{ArrowDown}');
      expect(radio('opt_c')).toHaveFocus();
      expect(radio('opt_c')).toHaveAttribute('aria-checked', 'true');
    });

    it('moves focus and selection with ArrowLeft and ArrowUp', async () => {
      const user = userEvent.setup();
      render(<Controlled initial="c" />);
      await user.tab();
      await user.keyboard('{ArrowLeft}');
      expect(radio('opt_b')).toHaveFocus();
      await user.keyboard('{ArrowUp}');
      expect(radio('opt_a')).toHaveFocus();
      expect(radio('opt_a')).toHaveAttribute('aria-checked', 'true');
    });

    it('wraps around at both ends', async () => {
      const user = userEvent.setup();
      render(<Controlled initial="c" />);
      await user.tab();
      await user.keyboard('{ArrowRight}');
      expect(radio('opt_a')).toHaveFocus();
      await user.keyboard('{ArrowLeft}');
      expect(radio('opt_c')).toHaveFocus();
    });

    it('jumps to the first and last option with Home and End', async () => {
      const user = userEvent.setup();
      render(<Controlled initial="b" />);
      await user.tab();
      await user.keyboard('{End}');
      expect(radio('opt_c')).toHaveFocus();
      expect(radio('opt_c')).toHaveAttribute('aria-checked', 'true');
      await user.keyboard('{Home}');
      expect(radio('opt_a')).toHaveFocus();
      expect(radio('opt_a')).toHaveAttribute('aria-checked', 'true');
    });

    it('reports the new value through onChange', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<M3SegmentedRow options={OPTIONS} value="a" onChange={onChange} ariaLabel="choices" />);
      await user.tab();
      await user.keyboard('{ArrowRight}');
      expect(onChange).toHaveBeenCalledWith('b');
    });

    it('suppresses the default action of handled keys', () => {
      render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
      const first = screen.getByRole('radio', { name: 'opt_a' });
      expect(fireEvent.keyDown(first, { key: 'ArrowDown' })).toBe(false);
      expect(fireEvent.keyDown(first, { key: 'x' })).toBe(true);
    });

    it('leaves browser shortcuts such as Alt+ArrowLeft alone', () => {
      const onChange = vi.fn();
      render(<M3SegmentedRow options={OPTIONS} value="b" onChange={onChange} ariaLabel="choices" />);
      const second = screen.getByRole('radio', { name: 'opt_b' });
      expect(fireEvent.keyDown(second, { key: 'ArrowLeft', altKey: true })).toBe(true);
      expect(fireEvent.keyDown(second, { key: 'ArrowLeft', metaKey: true })).toBe(true);
      expect(fireEvent.keyDown(second, { key: 'Home', ctrlKey: true })).toBe(true);
      expect(onChange).not.toHaveBeenCalled();
    });

    it('keeps stepping from the focused radio when the parent does not update the value', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<M3SegmentedRow options={OPTIONS} value="a" onChange={onChange} ariaLabel="choices" />);
      await user.tab();
      await user.keyboard('{ArrowRight}{ArrowRight}');
      expect(onChange).toHaveBeenNthCalledWith(1, 'b');
      expect(onChange).toHaveBeenNthCalledWith(2, 'c');
      expect(radio('opt_c')).toHaveFocus();
    });

    it('ignores other keys', async () => {
      const user = userEvent.setup();
      const onChange = vi.fn();
      render(<M3SegmentedRow options={OPTIONS} value="a" onChange={onChange} ariaLabel="choices" />);
      await user.tab();
      await user.keyboard('x');
      expect(onChange).not.toHaveBeenCalled();
      expect(radio('opt_a')).toHaveFocus();
    });
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<M3SegmentedRow options={OPTIONS} value="a" onChange={vi.fn()} ariaLabel="choices" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
