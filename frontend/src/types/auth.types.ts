export type Role = 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'MANAGER' | 'GUEST';

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface RegisterRequest {
  username: string;
  password?: string;
  email: string;
  role: Role;
}



export interface UserPayload {
  sub: string;
  username: string;
  role: Role;
  iat?: number;
  exp?: number;
}
