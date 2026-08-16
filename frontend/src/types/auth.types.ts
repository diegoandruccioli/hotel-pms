export type Role = 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'GUEST';

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface UserPayload {
  sub: string;
  username: string;
  role: Role;
  mustChangePassword?: boolean;
  iat?: number;
  exp?: number;
}
