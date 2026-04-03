import { render, screen } from '@testing-library/react';
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
});
