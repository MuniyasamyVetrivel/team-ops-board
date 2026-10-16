import { useSearchParams } from 'react-router';

import { PageHeader } from '@/components/common/PageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { WorkflowsPanel } from '@/features/approvals/WorkflowsPanel';
import { canManageModules, hasPermission } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { ModulesPanel } from '@/features/modules/ModulesPanel';

import { GeneralSettings } from './GeneralSettings';
import { PermissionMatrix } from './PermissionMatrix';

type Tab = 'general' | 'permissions' | 'workflows' | 'modules';

/**
 * System settings (SETTINGS_MANAGE): thresholds and defaults, the role permission matrix (PERMISSION_MANAGE; only a
 * Super Admin edits it), approval workflows (APPROVAL_CONFIGURE) and department tools switched on per department
 * (MODULE_MANAGE). The tab is kept in the URL (?tab=).
 */
export default function SettingsPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const tabs: { id: Tab; label: string }[] = [
    { id: 'general', label: 'General' },
    ...(hasPermission(user, 'PERMISSION_MANAGE') ? [{ id: 'permissions' as Tab, label: 'Roles & permissions' }] : []),
    ...(hasPermission(user, 'APPROVAL_CONFIGURE') ? [{ id: 'workflows' as Tab, label: 'Approval workflows' }] : []),
    ...(canManageModules(user) ? [{ id: 'modules' as Tab, label: 'Modules' }] : []),
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
      <PageHeader title="Settings" description="Thresholds and defaults, role permissions, approval workflows and department tools." />
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
        {tab === 'modules' && (
          <TabsContent value="modules" className="pt-5">
            <ModulesPanel />
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
