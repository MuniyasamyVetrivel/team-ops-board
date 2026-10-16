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
import { useDepartmentTools } from '@/features/modules/api';
import { cn } from '@/lib/utils';

import { BENCH_KEY, BENCH_PATHS } from './bench-paths';
import { BenchToolContext } from './bench-tool';

const TABS = [
  { to: BENCH_PATHS.dashboard, label: 'Dashboard', end: true },
  { to: BENCH_PATHS.bench, label: 'Bench' },
  { to: BENCH_PATHS.consultants, label: 'Consultants' },
  { to: BENCH_PATHS.allocations, label: 'Allocations' },
  { to: BENCH_PATHS.engagements, label: 'Engagements' },
  { to: BENCH_PATHS.clients, label: 'Clients' },
];

/**
 * Wraps every /tools/bench page: the tool must be in the user's department tools (switched on for their department
 * and BENCH_VIEW), which the server enforces on every request as well. Pages read `canEdit` from {@link useBenchTool}.
 */
export function BenchLayout() {
  const tools = useDepartmentTools();

  if (tools.isPending) {
    return (
      <div className="space-y-6" aria-busy="true" aria-label="Loading Bench Management">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-9 w-full max-w-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }
  if (tools.isError) return <ErrorState error={tools.error} title="Couldn't load Bench Management" onRetry={() => void tools.refetch()} />;

  const tool = tools.data.find((t) => t.key === BENCH_KEY);
  if (!tool) {
    return (
      <Card>
        <EmptyState
          icon={Ban}
          title="This tool isn't available to you"
          description="Bench Management isn't switched on for your department, or you don't have permission to open it. Ask your Super Admin if you need it."
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
    <BenchToolContext.Provider value={tool}>
      <div className="space-y-6">
        <div className="space-y-4">
          <PageHeader title={tool.name} description={tool.description} actions={<Badge tone={tool.canEdit ? 'primary' : 'neutral'}>{tool.canEdit ? 'Can edit' : 'View only'}</Badge>} />
          <nav aria-label="Bench Management" className="-mx-1 flex gap-1 overflow-x-auto border-b px-1 [scrollbar-width:none]">
            {TABS.map((tab) => (
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
    </BenchToolContext.Provider>
  );
}
