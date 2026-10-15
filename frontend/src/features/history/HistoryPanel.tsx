import { ChevronDown, History } from 'lucide-react';
import { useId, useState } from 'react';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

import type { HistoryRecordType } from './api';
import { RecordHistory } from './RecordHistory';

/** A record page's History section; the timeline loads when it is opened. */
export function HistoryPanel({ type, id, className }: { type: HistoryRecordType; id: number; className?: string }) {
  const [open, setOpen] = useState(false);
  const contentId = useId();
  return (
    <Card className={cn('overflow-hidden', className)}>
      <div className="flex items-center justify-between gap-3 px-6 py-4">
        <h2 className="flex items-center gap-2 text-base font-semibold">
          <History className="size-4 text-muted-foreground" aria-hidden />
          History
        </h2>
        <Button variant="ghost" size="sm" aria-expanded={open} aria-controls={contentId} onClick={() => setOpen((value) => !value)}>
          {open ? 'Hide history' : 'Show history'}
          <ChevronDown className={cn('transition-transform', open && 'rotate-180')} aria-hidden />
        </Button>
      </div>
      {open && (
        <div id={contentId} className="border-t px-6 py-5">
          <RecordHistory type={type} id={id} />
        </div>
      )}
    </Card>
  );
}
