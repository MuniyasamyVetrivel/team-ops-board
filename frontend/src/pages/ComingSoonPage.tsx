import { EmptyState } from '@/components/common/EmptyState';
import { PageHeader } from '@/components/common/PageHeader';
import { Card } from '@/components/ui/card';
import type { NavItem } from '@/config/navigation';

/** Placeholder for modules that are not built yet; names the implementation phase that delivers them. */
export default function ComingSoonPage({ item }: { item: NavItem }) {
  return (
    <div className="space-y-6">
      <PageHeader title={item.label} description={item.description} />
      <Card>
        <EmptyState
          icon={item.icon}
          title={`${item.label} is coming soon`}
          description={`This module is planned for Phase ${item.phase} of the implementation plan.`}
        />
      </Card>
    </div>
  );
}
