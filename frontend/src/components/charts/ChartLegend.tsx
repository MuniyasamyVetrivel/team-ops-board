import { cn } from '@/lib/utils';

export interface LegendItem {
  label: string;
  color: string;
  /** Lines show a short stroke (dashed when set) instead of a dot. */
  shape?: 'dot' | 'line' | 'dashed';
}

/** The one chart legend: every colour named in words, never relied on alone. */
export function ChartLegend({ items, className }: { items: LegendItem[]; className?: string }) {
  return (
    <ul className={cn('flex flex-wrap items-center gap-x-5 gap-y-1.5 text-label text-muted-foreground', className)} aria-label="Chart legend">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-2 whitespace-nowrap">
          {item.shape === 'line' || item.shape === 'dashed' ? (
            <span className="w-4 border-t-2" style={{ borderColor: item.color, borderStyle: item.shape === 'dashed' ? 'dashed' : 'solid' }} aria-hidden />
          ) : (
            <span className="size-2.5 rounded-full" style={{ background: item.color }} aria-hidden />
          )}
          {item.label}
        </li>
      ))}
    </ul>
  );
}
