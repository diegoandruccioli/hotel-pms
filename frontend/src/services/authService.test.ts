import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authService } from './authService';
import api from './api';

vi.mock('./api');

describe('authService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockUserPayload = {
    sub: 'user-1',
    username: 'test',
    role: 'ADMIN' as const
  };

  it('should call login correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: undefined });
    await authService.login({ username: 'test', password: 'password' });
    expect(api.post).toHaveBeenCalledWith('/api/v1/auth/login', {
      username: 'test',
      password: 'password'
    });
  });

  it('should call register correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: undefined });
    await authService.register({
      username: 'test',
      password: 'password',
      email: 'test@example.com',
      role: 'ADMIN'
    });
    expect(api.post).toHaveBeenCalledWith('/api/v1/auth/register', {
      username: 'test',
      password: 'password',
      email: 'test@example.com',
      role: 'ADMIN'
    });
  });

  it('should call logout correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: undefined });
    await authService.logout();
    expect(api.post).toHaveBeenCalledWith('/api/v1/auth/logout');
  });

  it('should fetch user correctly', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockUserPayload });
    const user = await authService.fetchMe();
    expect(api.get).toHaveBeenCalledWith('/api/v1/auth/me');
    expect(user).toEqual(mockUserPayload);
  });
});
