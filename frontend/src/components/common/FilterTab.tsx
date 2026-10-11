import type { ComponentProps, ReactNode } from 'react';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';

/**
 * A row of filter tabs: all in one row on desktop ({@code columns} of them), wrapping to 2 or 3 columns on smaller
 * screens. Pass the tab count so the desktop row has no empty cells.
 */
export function FilterTabs({ columns, className, ...props }: ComponentProps<'div'> & { columns: 3 | 4 | 5 | 6 }) {
  const desktop = { 3: 'lg:grid-cols-3', 4: 'lg:grid-cols-4', 5: 'lg:grid-cols-5', 6: 'lg:grid-cols-6' }[columns];
  return <div role="tablist" className={cn('grid grid-cols-2 gap-3 sm:grid-cols-3', desktop, className)} {...props} />;
}

interface FilterTabProps {
  label: string;
  count: number | null | undefined;
  active: boolean;
  onSelect: () => void;
  loading?: boolean;
  /** Colours the count when it needs attention (e.g. overdue > 0); the label still names it. */
  countTone?: string;
  /** A small icon before the label. */
  icon?: ReactNode;
  /** A toggle filter (pressed / not pressed) in a role="group" row, rather than a tab. */
  toggle?: boolean;
}

/** A slim (56px) card-like tab that filters the list below: label on the left, count on the right. */
export function FilterTab({ label, count, active, onSelect, loading, countTone, icon, toggle = false }: FilterTabProps) {
  return (
    <button
      type="button"
      role={toggle ? undefined : 'tab'}
      aria-selected={toggle ? undefined : active}
      aria-pressed={toggle ? active : undefined}
      onClick={onSelect}
      className={cn(
        'flex h-14 min-w-0 items-center justify-between gap-3 rounded-xl border bg-card px-4 text-left transition-[border-color,background-color,box-shadow] outline-none focus-visible:ring-[3px] focus-visible:ring-ring/40',
        active ? 'border-primary bg-accent shadow-xs' : 'shadow-card hover:border-primary/40',
      )}
    >
      <span className={cn('flex min-w-0 items-center gap-2 text-label font-medium', active ? 'text-accent-foreground' : 'text-muted-foreground')}>
        {icon}
        <span className="truncate" title={label}>
          {label}
        </span>
      </span>
      {loading ? (
        <Skeleton className="h-6 w-8" />
      ) : (
        <span className={cn('text-xl font-semibold tabular-nums', count === 0 || count == null ? 'text-muted-foreground' : (countTone ?? (active ? 'text-accent-foreground' : 'text-foreground')))}>
          {count == null ? '—' : count.toLocaleString()}
        </span>
      )}
    </button>
  );
}
