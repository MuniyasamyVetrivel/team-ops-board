import type { PermissionCode, RoleCode } from './permissions';

export interface DepartmentSummary {
  id: number;
  name: string;
  code: string;
}

/** Mirrors com.teamops.auth.dto.MeResponse - authorities are computed by the backend. */
export interface CurrentUser {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  jobTitle: string | null;
  department: DepartmentSummary;
  roles: RoleCode[];
  permissions: PermissionCode[];
}

/** Mirrors com.teamops.auth.dto.AuthResponse. */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  user: CurrentUser;
}

export interface LoginCredentials {
  email: string;
  password: string;
}
