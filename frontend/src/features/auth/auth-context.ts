import { createContext } from 'react';

import type { ChangePasswordInput, CurrentUser, LoginCredentials } from './types';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  login: (credentials: LoginCredentials) => Promise<CurrentUser>;
  changePassword: (input: ChangePasswordInput) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  /** Applies a change the server confirmed to the signed-in user (for example a new profile photo). */
  updateUser: (patch: Partial<CurrentUser>) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
