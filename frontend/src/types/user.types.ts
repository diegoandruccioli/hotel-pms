import type { Role } from './auth.types';

export interface UserResponse {
  id: string;
  username: string;
  email: string;
  role: Role;
  active: boolean;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  email: string;
  role: Role;
}
