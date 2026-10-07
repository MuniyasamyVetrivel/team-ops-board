import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api/client';
import { cleanParams, type PageResponse, type UserStatus, type UserSummary } from '@/lib/api/types';

/** Mirrors com.teamops.team.dto.TeamMemberItem. */
export interface TeamMember {
  id: number;
  fullName: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string | null;
  department: { id: number; name: string; code: string };
  manager: UserSummary | null;
  phone: string | null;
  location: string | null;
  workingHours: string | null;
  status: UserStatus;
}

/** Mirrors com.teamops.team.dto.TeamMemberProfile. */
export interface TeamMemberProfile {
  member: TeamMember;
  roles: string[];
  memberSince: string;
  directReports: UserSummary[];
  canViewWork: boolean;
}

export interface TeamQuery {
  search?: string;
  departmentId?: number;
  status?: UserStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export const teamKeys = {
  all: ['team'] as const,
  directory: (query: TeamQuery) => [...teamKeys.all, 'directory', query] as const,
  profile: (id: number) => [...teamKeys.all, 'profile', id] as const,
};

export function useTeamDirectory(query: TeamQuery, enabled = true) {
  return useQuery({
    queryKey: teamKeys.directory(query),
    queryFn: async () => (await api.get<PageResponse<TeamMember>>('/team', { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useTeamProfile(id: number) {
  return useQuery({
    queryKey: teamKeys.profile(id),
    queryFn: async () => (await api.get<TeamMemberProfile>(`/team/${id}`)).data,
    enabled: Number.isFinite(id),
  });
}
