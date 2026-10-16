import { cn } from '@/lib/utils';

import type { Allocation } from './api';
import { buildTimeline, dateRange, PERIOD_STATUS_META } from './bench-meta';

/**
 * A consultant's allocations as lanes on a month axis: one bar per allocation, solid for billable and hatched for
 * non-billable (the lane label says which, so colour is never the only cue), faded once ended, and a marker for today
 * (the server's business date). Open-ended allocations run off the right edge.
 */
export function AllocationTimeline({ allocations, today, onOpen }: { allocations: Allocation[]; today: string | null; onOpen: (id: number) => void }) {
  const timeline = buildTimeline(allocations, today);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-muted-foreground" aria-hidden>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-5 rounded-sm bg-primary" /> Billable
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-2.5 w-5 rounded-sm border border-primary bg-[repeating-linear-gradient(135deg,var(--accent)_0_4px,transparent_4px_8px)]" /> Non-billable
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-3 w-0.5 bg-highlight" /> Today
        </span>
      </div>

      <div className="grid grid-cols-1 gap-y-2 sm:grid-cols-[minmax(0,13rem)_minmax(0,1fr)] sm:gap-x-4">
        {/* Month axis */}
        <div className="hidden sm:block" />
        <div className="relative h-6 border-b text-xs text-muted-foreground" aria-hidden>
          {timeline.months.map((m) => (
            <span key={m.key} className="absolute top-0 border-l pl-1 whitespace-nowrap" style={{ left: `${m.left}%` }}>
              {m.label}
            </span>
          ))}
        </div>

        <ol className="contents" aria-label="Allocation timeline">
          {timeline.bars.map(({ item: a, left, width, openEnded }) => {
            const status = PERIOD_STATUS_META[a.status];
            const label = `${a.engagement.client.name} · ${a.engagement.name}: ${a.role}, ${a.allocationPercent}%, ${a.billable ? 'billable' : 'non-billable'}, ${dateRange(a.startDate, a.endDate)}, ${status.label.toLowerCase()}`;
            return (
              <li key={a.id} className="contents">
                <div className="min-w-0 pt-1 sm:pt-0">
                  <p className="truncate text-sm font-medium" title={a.engagement.name}>
                    {a.engagement.name}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">
                    {a.engagement.client.name} · {a.allocationPercent}% · {a.billable ? 'Billable' : 'Non-billable'} · {status.label}
                  </p>
                </div>
                <div className="relative h-9 rounded-md bg-muted/40">
                  {timeline.months.map((m) => (
                    <span key={m.key} className="absolute inset-y-0 border-l border-border/60" style={{ left: `${m.left}%` }} aria-hidden />
                  ))}
                  <button
                    type="button"
                    aria-label={label}
                    title={label}
                    onClick={() => onOpen(a.id)}
                    className={cn(
                      'absolute inset-y-1 flex items-center overflow-hidden rounded px-2 text-left text-xs font-medium whitespace-nowrap transition-[filter] outline-none hover:brightness-95 focus-visible:ring-[3px] focus-visible:ring-ring/50',
                      a.billable ? 'bg-primary text-primary-foreground' : 'border border-primary bg-[repeating-linear-gradient(135deg,var(--accent)_0_4px,var(--card)_4px_8px)] text-accent-foreground',
                      a.status === 'ENDED' && 'opacity-50',
                      openEnded && 'rounded-r-none',
                    )}
                    style={{ left: `${left}%`, width: `${width}%` }}
                  >
                    <span className="truncate">
                      {a.allocationPercent}% · {a.role}
                    </span>
                  </button>
                  {timeline.today !== null && <span className="pointer-events-none absolute -inset-y-1 w-0.5 bg-highlight" style={{ left: `${timeline.today}%` }} aria-hidden />}
                </div>
              </li>
            );
          })}
        </ol>
      </div>
    </div>
  );
}
