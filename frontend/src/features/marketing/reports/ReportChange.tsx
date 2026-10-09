import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';

import { cn } from '@/lib/utils';

import { formatPercent } from '../marketing-format';
import type { ReportLine } from './api';
import { formatReportValue } from './report-meta';

/** The change in words with an arrow: "Up 11.11%", "Down 3.84 pts", "No change"; green or red only for good or bad news. */
export function ReportChange({ line, className }: { line: ReportLine; className?: string }) {
  if (line.change === null) return <span className={cn('text-xs text-muted-foreground', className)}>—</span>;
  if (line.change === 0) {
    return (
      <span className={cn('inline-flex items-center gap-1 text-xs text-muted-foreground', className)}>
        <Minus className="size-3.5" aria-hidden />
        No change
      </span>
    );
  }
  const up = line.change > 0;
  const good = line.better === 'NEITHER' ? null : up === (line.better === 'HIGHER');
  const Icon = up ? ArrowUpRight : ArrowDownRight;
  const amount =
    line.unit === 'PERCENT'
      ? `${Math.abs(line.change).toFixed(2)} pts`
      : line.changePct !== null
        ? `${formatPercent(Math.abs(line.changePct))}`
        : formatReportValue(Math.abs(line.change), line.unit);
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-medium tabular-nums', good === null ? 'text-muted-foreground' : good ? 'text-status-success' : 'text-status-danger', className)}>
      <Icon className="size-3.5" aria-hidden />
      {up ? 'Up' : 'Down'} {amount}
    </span>
  );
}
