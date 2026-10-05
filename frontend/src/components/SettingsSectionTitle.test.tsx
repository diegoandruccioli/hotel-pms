import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { SettingsSectionTitle } from './SettingsSectionTitle';

describe('SettingsSectionTitle', () => {
  it('renders the title as a level-2 heading, below the page h1', () => {
    render(<SettingsSectionTitle title="Password" />);
    expect(screen.getByRole('heading', { level: 2, name: 'Password' })).toBeInTheDocument();
  });

  it('renders the subtitle only when given', () => {
    const { rerender } = render(<SettingsSectionTitle title="Password" />);
    expect(screen.queryByText('Change it')).not.toBeInTheDocument();
    rerender(<SettingsSectionTitle title="Password" subtitle="Change it" />);
    expect(screen.getByText('Change it')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    const { container } = render(<SettingsSectionTitle title="Password" subtitle="Change it" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
