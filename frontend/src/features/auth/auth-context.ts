import { createContext } from 'react';

import type { CurrentUser, LoginCredentials } from './types';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  login: (credentials: LoginCredentials) => Promise<CurrentUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
