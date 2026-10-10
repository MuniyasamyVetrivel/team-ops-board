import { useSearchParams } from 'react-router';

import { PageHeader } from '@/components/common/PageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { WorkflowsPanel } from '@/features/approvals/WorkflowsPanel';
import { hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';

import { GeneralSettings } from './GeneralSettings';
import { PermissionMatrix } from './PermissionMatrix';

type Tab = 'general' | 'permissions' | 'workflows';

/**
 * System settings (SETTINGS_MANAGE): thresholds and defaults, the role permission matrix (PERMISSION_MANAGE; only a
 * Super Admin edits it) and approval workflows (APPROVAL_CONFIGURE). The tab is kept in the URL (?tab=).
 */
export default function SettingsPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const tabs: { id: Tab; label: string }[] = [
    { id: 'general', label: 'General' },
    ...(hasPermission(user, 'PERMISSION_MANAGE') ? [{ id: 'permissions' as Tab, label: 'Roles & permissions' }] : []),
    ...(hasPermission(user, 'APPROVAL_CONFIGURE') ? [{ id: 'workflows' as Tab, label: 'Approval workflows' }] : []),
  ];
  const requested = params.get('tab');
  const tab: Tab = tabs.find((t) => t.id === requested)?.id ?? 'general';

  const selectTab = (next: string) =>
    setParams(
      (existing) => {
        const updated = new URLSearchParams(existing);
        if (next === 'general') updated.delete('tab');
        else updated.set('tab', next);
        return updated;
      },
      { replace: true },
    );

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="Thresholds and defaults, role permissions and approval workflows." />
      <Tabs value={tab} onValueChange={selectTab}>
        <TabsList className="px-0">
          {tabs.map((t) => (
            <TabsTrigger key={t.id} value={t.id}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="general" className="pt-5">
          <GeneralSettings />
        </TabsContent>
        <TabsContent value="permissions" className="pt-5">
          <PermissionMatrix />
        </TabsContent>
        <TabsContent value="workflows" className="pt-5">
          <WorkflowsPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
