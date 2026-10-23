import { createContext, useContext } from 'react';
import type { User } from './types';

export interface AuthState {
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return context;
}

export function canWrite(user: User | null): boolean {
  return user?.role === 'ADMIN' || user?.role === 'DEVELOPER';
}

export function isAdmin(user: User | null): boolean {
  return user?.role === 'ADMIN';
}
