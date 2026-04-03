import { describe, it, expect, beforeEach } from 'vitest';
import { useAuthStore } from './authStore';
import type { UserPayload } from '../types/auth.types';

describe('authStore', () => {
  beforeEach(() => {
    // Reset state before each test
    useAuthStore.setState({
      user: null,
      isAuthenticated: false,
      isLoading: true,
    });
  });

  it('should initialize with correct default state', () => {
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isLoading).toBe(true);
  });

  it('should handle login', () => {
    const user: UserPayload = { sub: 'user1', username: 'testuser', role: 'ADMIN' };
    useAuthStore.getState().login(user);
    
    const state = useAuthStore.getState();
    expect(state.user).toEqual(user);
    expect(state.isAuthenticated).toBe(true);
  });

  it('should handle logout', () => {
    const user: UserPayload = { sub: 'user1', username: 'testuser', role: 'ADMIN' };
    useAuthStore.getState().login(user);
    useAuthStore.getState().logout();
    
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
  });

  it('should handle checkAuth with user', () => {
    const user: UserPayload = { sub: 'user1', username: 'testuser', role: 'ADMIN' };
    useAuthStore.getState().checkAuth(user);
    
    const state = useAuthStore.getState();
    expect(state.user).toEqual(user);
    expect(state.isAuthenticated).toBe(true);
    expect(state.isLoading).toBe(false);
  });

  it('should handle checkAuth without user', () => {
    useAuthStore.getState().checkAuth(null);
    
    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.isLoading).toBe(false);
  });
});
