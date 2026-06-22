import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { M3TextField } from './M3TextField';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}));

describe('M3TextField', () => {
  it('should render with label', () => {
    render(<M3TextField label="Email" name="email" />);
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('should render leading icon when provided', () => {
    render(<M3TextField label="User" leadingIcon="person" name="user" />);
    expect(screen.getByText('person')).toBeInTheDocument();
  });

  it('should show error text when provided', () => {
    render(<M3TextField label="Email" errorText="Required field" name="email" />);
    expect(screen.getByText('Required field')).toBeInTheDocument();
  });

  it('should set aria-invalid when errorText is provided', () => {
    render(<M3TextField label="Email" errorText="Required" name="email" />);
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('should show supporting text when provided', () => {
    render(<M3TextField label="Email" supportingText="Enter your work email" name="email" />);
    expect(screen.getByText('Enter your work email')).toBeInTheDocument();
  });

  it('should handle focus and blur', () => {
    render(<M3TextField label="Name" name="name" />);
    const input = screen.getByLabelText('Name');

    fireEvent.focus(input);
    fireEvent.blur(input);

    // Should not throw and input should still be in the DOM
    expect(input).toBeInTheDocument();
  });

  it('should pass value and onChange', () => {
    const handleChange = vi.fn();
    render(<M3TextField label="Test" name="test" value="hello" onChange={handleChange} />);
    const input = screen.getByLabelText('Test');
    expect(input).toHaveValue('hello');
  });

  it('should have no accessibility violations', async () => {
    const { container } = render(<M3TextField label="Email" name="email" />);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('should not render a visibility toggle for non-password fields', () => {
    render(<M3TextField label="Email" name="email" />);
    expect(screen.queryByLabelText('show_password')).not.toBeInTheDocument();
  });

  it('should render a visibility toggle for password fields, masked by default', () => {
    render(<M3TextField label="Password" name="password" type="password" />);
    const input = screen.getByLabelText('Password');
    expect(input).toHaveAttribute('type', 'password');
    expect(screen.getByLabelText('show_password')).toBeInTheDocument();
  });

  it('should reveal the password as plain text when the toggle is clicked', () => {
    render(<M3TextField label="Password" name="password" type="password" />);
    const input = screen.getByLabelText('Password');

    fireEvent.click(screen.getByLabelText('show_password'));

    expect(input).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('hide_password')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('hide_password'));

    expect(input).toHaveAttribute('type', 'password');
  });
});
