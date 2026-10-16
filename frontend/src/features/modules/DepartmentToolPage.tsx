import { Ban } from 'lucide-react';
import { Link, useParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { PageHeader } from '@/components/common/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

import { useDepartmentTools } from './api';
import { moduleIcon } from './module-icons';

/**
 * The landing page of a department tool (/tools/:key). Each module replaces this with its own pages when it is built
 * (docs/adding-a-module.md); until then it names the tool. A tool that is off for the user's departments, or not
 * permitted to them, is not offered (the server answers 403 MODULE_DISABLED for its API as well).
 */
export default function DepartmentToolPage() {
  const { key } = useParams<{ key: string }>();
  const tools = useDepartmentTools();

  if (tools.isPending) {
    return (
      <div className="space-y-6" aria-busy="true" aria-label="Loading tool">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }
  if (tools.isError) return <ErrorState error={tools.error} title="Couldn't load this tool" onRetry={() => void tools.refetch()} />;

  const tool = tools.data.find((t) => t.key === key);
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

  const Icon = moduleIcon(tool.icon);
  return (
    <div className="space-y-6">
      <PageHeader
        title={tool.name}
        description={tool.description}
        actions={<Badge tone={tool.canEdit ? 'primary' : 'neutral'}>{tool.canEdit ? 'Can edit' : 'View only'}</Badge>}
      />
      <Card>
        <EmptyState icon={Icon} title={`${tool.name} is being set up`} description="Its pages will appear here soon." />
      </Card>
    </div>
  );
}
