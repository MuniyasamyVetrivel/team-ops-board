/** Shared API shapes. Mirrors com.teamops.common.web.PageResponse and com.teamops.user.dto.UserSummary. */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type UserStatus = 'ACTIVE' | 'DISABLED';

export interface UserSummary {
  id: number;
  fullName: string;
  email: string;
  jobTitle: string | null;
  status: UserStatus;
}

/** Drops empty values so they are not sent as query parameters. */
export function cleanParams<T extends object>(params: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  ) as Partial<T>;
}
