import type { ReactNode } from 'react';

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { cn } from '@/lib/utils';

import { useCanViewHistory, type HistoryRecordType } from './api';
import { RecordHistory } from './RecordHistory';

/**
 * Details | History tabs for a record drawer, page or edit dialog that has no tabs of its own. The details stay as they
 * were; the History tab loads only when opened. Without an id (a record being created), or for people without
 * HISTORY_VIEW, only the details are shown.
 */
export function DetailsWithHistory({
  type,
  id,
  children,
  detailsLabel = 'Details',
  className,
  listClassName,
}: {
  type: HistoryRecordType;
  id: number | null;
  children: ReactNode;
  detailsLabel?: string;
  className?: string;
  listClassName?: string;
}) {
  const canViewHistory = useCanViewHistory();
  if (id === null || !canViewHistory) return <>{children}</>;
  return (
    <Tabs defaultValue="details" className={cn('flex min-h-0 flex-1 flex-col', className)}>
      <TabsList className={listClassName}>
        <TabsTrigger value="details">{detailsLabel}</TabsTrigger>
        <TabsTrigger value="history">History</TabsTrigger>
      </TabsList>
      <TabsContent value="details" className="flex min-h-0 flex-1 flex-col">
        {children}
      </TabsContent>
      <TabsContent value="history" className="min-h-0 flex-1 overflow-y-auto px-6 py-5">
        <RecordHistory type={type} id={id} />
      </TabsContent>
    </Tabs>
  );
}
