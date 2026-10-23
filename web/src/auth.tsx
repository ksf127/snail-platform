import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { api, tokenStore } from './api';
import { AuthContext } from './auth-context';
import type { LoginResponse, User } from './types';

const USER_KEY = 'platform.user';

function readUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readUser);

  const login = useCallback(async (username: string, password: string) => {
    const result = await api.post<LoginResponse>('/auth/login', { username, password });
    tokenStore.set(result.token);
    localStorage.setItem(USER_KEY, JSON.stringify(result.user));
    setUser(result.user);
  }, []);

  const logout = useCallback(() => {
    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
