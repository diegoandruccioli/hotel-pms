import { render, screen, fireEvent } from '@testing-library/react';
import { ToastContainer } from './Toast';
import { useToastStore } from '../store/toastStore';
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

  it('renders nothing when there are no toasts', () => {
    const { container } = render(<ToastContainer />);
    expect(container).toBeEmptyDOMElement();
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
