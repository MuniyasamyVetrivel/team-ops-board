import { ArrowDown, ArrowUp, Minus } from 'lucide-react';

import { cn } from '@/lib/utils';

export interface DeltaProps {
  /** Signed change; it sets the direction. {@code null} means there is nothing to compare with and renders nothing. */
  value: number | null | undefined;
  /** The formatted size of the change, e.g. "11.1%", "2.60 pts" or "₹300". Defaults to |value| with {@code suffix}. */
  amount?: string;
  /** Appended to the default amount, e.g. "%" or " pts". */
  suffix?: string;
  /** Trailing comparison text, e.g. "vs last week". In the chip it is read out but not shown. */
  label?: string;
  /** Whether a rise is good news (default), bad news (overdue), or neither (null: shown in grey). */
  better?: 'higher' | 'lower' | null;
  /** "inline" (tables, rows): coloured text. "chip" (KPI tiles): 12px on a tinted background, arrow and number only. */
  variant?: 'inline' | 'chip';
  className?: string;
}

/**
 * "↑ 8 vs last week", "↓ 5", "— 0", always on one line. Direction is carried by the arrow and by screen-reader text
 * ("Up", "Down", "No change"); colour adds meaning only when the direction is clearly good (green) or bad (red).
 */
export function Delta({ value, amount, suffix = '', label, better = 'higher', variant = 'inline', className }: DeltaProps) {
  if (value == null) return null;
  const Icon = value > 0 ? ArrowUp : value < 0 ? ArrowDown : Minus;
  const good = value === 0 || better === null ? null : (value > 0) === (better === 'higher');
  const size = amount ?? `${Math.abs(value).toLocaleString()}${suffix}`;
  const direction = <span className="sr-only">{value > 0 ? 'Up ' : value < 0 ? 'Down ' : 'No change '}</span>;
  const tone = good === null ? 'text-muted-foreground' : good ? 'text-status-success' : 'text-status-danger';

  if (variant === 'chip') {
    return (
      <span
        className={cn(
          'inline-flex shrink-0 items-center gap-0.5 rounded-md px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap tabular-nums',
          tone,
          good === null ? 'bg-muted' : good ? 'bg-status-success/10' : 'bg-status-danger/10',
          className,
        )}
        title={label ? `${value > 0 ? 'Up' : value < 0 ? 'Down' : 'No change'} ${size} ${label}` : undefined}
      >
        <Icon className="size-3 shrink-0" strokeWidth={2.75} aria-hidden />
        {direction}
        {size}
        {label && <span className="sr-only">{` ${label}`}</span>}
      </span>
    );
  }
  return (
    <span className={cn('inline-flex items-center gap-1 text-label font-medium whitespace-nowrap tabular-nums', tone, className)}>
      <Icon className="size-3.5 shrink-0" strokeWidth={2.5} aria-hidden />
      {direction}
      {`${size}${label ? ` ${label}` : ''}`}
    </span>
  );
}
