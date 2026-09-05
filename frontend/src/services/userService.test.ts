import { describe, it, expect, vi, beforeEach } from 'vitest';
import { userService } from './userService';
import api from './api';
import type { UserResponse } from '../types';

vi.mock('./api');

describe('userService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockUser: UserResponse = {
    id: 'user-1',
    username: 'test',
    email: 'test@hotel.com',
    role: 'ADMIN',
    active: true,
    mustChangePassword: false,
    createdAt: '2026-01-01T00:00:00Z',
  };

  it('lists users', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [mockUser] });
    const users = await userService.listUsers();
    expect(api.get).toHaveBeenCalledWith('/api/v1/auth/users');
    expect(users).toEqual([mockUser]);
  });

  it('creates a user', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockUser });
    const created = await userService.createUser({
      username: 'test',
      email: 'test@hotel.com',
      password: 'password123',
      role: 'ADMIN',
    });
    expect(api.post).toHaveBeenCalledWith('/api/v1/auth/users', {
      username: 'test',
      email: 'test@hotel.com',
      password: 'password123',
      role: 'ADMIN',
    });
    expect(created).toEqual(mockUser);
  });

  it('deactivates a user', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: { ...mockUser, active: false } });
    const result = await userService.deactivateUser('user-1');
    expect(api.patch).toHaveBeenCalledWith('/api/v1/auth/users/user-1/deactivate');
    expect(result.active).toBe(false);
  });

  it('activates a user', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockUser });
    const result = await userService.activateUser('user-1');
    expect(api.patch).toHaveBeenCalledWith('/api/v1/auth/users/user-1/activate');
    expect(result).toEqual(mockUser);
  });

  it('resets a user password', async () => {
    vi.mocked(api.patch).mockResolvedValueOnce({ data: undefined });
    await userService.resetUserPassword('user-1', 'newPassword123');
    expect(api.patch).toHaveBeenCalledWith('/api/v1/auth/users/user-1/reset-password', {
      newPassword: 'newPassword123',
    });
  });
});
