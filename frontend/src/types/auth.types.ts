export type Role = 'ADMIN' | 'OWNER' | 'RECEPTIONIST' | 'GUEST';

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
  mustChangePassword?: boolean;
  iat?: number;
  exp?: number;
}
