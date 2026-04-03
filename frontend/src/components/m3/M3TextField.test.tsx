import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { M3TextField } from './M3TextField';

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
});

import { vi } from 'vitest';
