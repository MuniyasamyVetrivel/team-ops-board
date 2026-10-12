import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useMarketingContext } from '@/features/marketing/api';
import { useTeamDirectory } from '@/features/team/api';

export interface PersonOption {
  id: number;
  fullName: string;
  detail: string | null;
}

/**
 * Active app users to pick an event owner, a post owner or the user an employee is linked to: the team directory
 * (TEAM_VIEW), or else the Digital Marketing people every CELEBRATIONS_EDIT holder can see.
 */
export function usePeople(enabled = true): { people: PersonOption[]; loading: boolean } {
  const { user } = useAuth();
  const directoryAllowed = hasPermission(user, 'TEAM_VIEW');
  const directory = useTeamDirectory({ status: 'ACTIVE', size: 100, sort: 'name,asc' }, enabled && directoryAllowed);
  const marketing = useMarketingContext(enabled && !directoryAllowed && hasPermission(user, 'MARKETING_VIEW'));
  if (directoryAllowed) {
    return {
      people: (directory.data?.content ?? []).map((m) => ({ id: m.id, fullName: m.fullName, detail: m.department.name })),
      loading: directory.isPending,
    };
  }
  return {
    people: (marketing.data?.owners ?? []).map((o) => ({ id: o.id, fullName: o.fullName, detail: o.jobTitle })),
    loading: marketing.isPending && marketing.fetchStatus !== 'idle',
  };
}
