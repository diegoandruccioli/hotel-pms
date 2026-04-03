import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useToastStore } from './toastStore';

describe('toastStore', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
    vi.useFakeTimers();
  });

  it('should add a toast', () => {
    useToastStore.getState().addToast('Test message', 'success');

    const toasts = useToastStore.getState().toasts;
    expect(toasts).toHaveLength(1);
    expect(toasts[0].message).toBe('Test message');
    expect(toasts[0].type).toBe('success');
    expect(toasts[0].id).toBeDefined();
  });

  it('should auto-remove toast after 4 seconds', () => {
    useToastStore.getState().addToast('Auto-dismiss', 'info');
    expect(useToastStore.getState().toasts).toHaveLength(1);

    vi.advanceTimersByTime(4000);

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('should manually remove a toast', () => {
    useToastStore.getState().addToast('Manual dismiss', 'error');
    const id = useToastStore.getState().toasts[0].id;

    useToastStore.getState().removeToast(id);

    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('should support multiple toasts', () => {
    useToastStore.getState().addToast('Toast 1', 'success');
    useToastStore.getState().addToast('Toast 2', 'error');
    useToastStore.getState().addToast('Toast 3', 'info');

    expect(useToastStore.getState().toasts).toHaveLength(3);
  });
});
