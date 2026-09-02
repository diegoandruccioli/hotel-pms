import { render, screen, fireEvent } from '@testing-library/react';
import { ToastContainer } from './Toast';
import { useToastStore } from '../store';
import { axe } from 'vitest-axe';
import { beforeEach, describe, it, expect } from 'vitest';

describe('Toast Component', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it('should have no accessibility violations when rendering a toast', async () => {
    useToastStore.setState({
      toasts: [{ id: '1', type: 'success', message: 'Test message' }]
    });

    const { container } = render(<ToastContainer />);

    // Verify toast is actually rendered
    expect(screen.getByText('Test message')).toBeInTheDocument();

    // Check accessibility violations
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it('renders no toast items when there are no toasts, but keeps the live regions mounted', () => {
    render(<ToastContainer />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    // The live regions themselves must stay in the DOM even when empty —
    // that's the whole point of the fix: a screen reader needs to have
    // already registered them before a toast can be reliably announced.
    expect(document.querySelector('[aria-live="polite"]')).toBeInTheDocument();
    expect(document.querySelector('[aria-live="assertive"]')).toBeInTheDocument();
  });

  it('routes an error toast into the assertive live region and others into the polite one', () => {
    useToastStore.setState({
      toasts: [
        { id: '1', type: 'error', message: 'Error message' },
        { id: '2', type: 'success', message: 'Success message' },
      ],
    });
    render(<ToastContainer />);

    const assertiveRegion = document.querySelector('[aria-live="assertive"]');
    const politeRegion = document.querySelector('[aria-live="polite"]');
    expect(assertiveRegion).toContainElement(screen.getByText('Error message'));
    expect(politeRegion).toContainElement(screen.getByText('Success message'));
  });

  it('renders error and info toast variants', () => {
    useToastStore.setState({
      toasts: [
        { id: '1', type: 'error', message: 'Error message' },
        { id: '2', type: 'info', message: 'Info message' },
      ],
    });
    render(<ToastContainer />);
    expect(screen.getByText('Error message')).toBeInTheDocument();
    expect(screen.getByText('Info message')).toBeInTheDocument();
    expect(screen.getAllByRole('alert')).toHaveLength(2);
  });

  it('removes a toast from the store when its dismiss button is clicked', () => {
    useToastStore.setState({
      toasts: [{ id: '1', type: 'success', message: 'Dismiss me' }],
    });
    render(<ToastContainer />);

    fireEvent.click(screen.getByRole('button'));

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });
});
