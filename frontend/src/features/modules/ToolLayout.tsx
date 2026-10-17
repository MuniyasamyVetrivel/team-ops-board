import { Ban } from 'lucide-react';
import { Suspense } from 'react';
import { Link, NavLink, Outlet } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

import { useDepartmentTools } from './api';
import { ToolContext } from './tool-context';

export interface ToolTab {
  to: string;
  label: string;
  /** Only active on exactly this path (the tool's landing page). */
  end?: boolean;
}

/**
 * Wraps every page of a built department tool (/tools/<key>/*): the tool must be in the user's department tools
 * (switched on for their department and its VIEW permission), which the server enforces on every request as well.
 * Shows the tool's title and tab bar; pages read `canEdit` from {@link useTool}.
 */
export function ToolLayout({ toolKey, tabs }: { toolKey: string; tabs: ToolTab[] }) {
  const tools = useDepartmentTools();

  if (tools.isPending) {
    return (
      <div className="space-y-6" aria-busy="true" aria-label="Loading the tool">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-9 w-full max-w-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }
  if (tools.isError) return <ErrorState error={tools.error} title="Couldn't load this tool" onRetry={() => void tools.refetch()} />;

  const tool = tools.data.find((t) => t.key === toolKey);
  if (!tool) {
    return (
      <Card>
        <EmptyState
          icon={Ban}
          title="This tool isn't available to you"
          description="It isn't switched on for your department, or you don't have permission to open it. Ask your Super Admin if you need it."
          action={
            <Button asChild variant="outline">
              <Link to="/dashboard">Back to dashboard</Link>
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <ToolContext.Provider value={tool}>
      <div className="space-y-6">
        <div className="space-y-4">
          <PageHeader title={tool.name} description={tool.description} actions={<Badge tone={tool.canEdit ? 'primary' : 'neutral'}>{tool.canEdit ? 'Can edit' : 'View only'}</Badge>} />
          <nav aria-label={tool.name} className="-mx-1 flex gap-1 overflow-x-auto border-b px-1 [scrollbar-width:none]">
            {tabs.map((tab) => (
              <NavLink
                key={tab.to}
                to={tab.to}
                end={tab.end}
                className={({ isActive }) =>
                  cn(
                    'shrink-0 px-3 py-2.5 text-sm font-medium whitespace-nowrap text-muted-foreground transition-colors outline-none hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:ring-inset',
                    isActive && 'text-foreground shadow-[inset_0_-2px_0_var(--primary)]',
                  )
                }
              >
                {tab.label}
              </NavLink>
            ))}
          </nav>
        </div>
        <Suspense fallback={<Skeleton className="h-96 rounded-xl" aria-label="Loading" />}>
          <Outlet />
        </Suspense>
      </div>
    </ToolContext.Provider>
  );
}
