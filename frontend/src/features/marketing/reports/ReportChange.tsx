import { Delta } from '@/components/common/Delta';
import { cn } from '@/lib/utils';

import { formatPercent } from '../marketing-format';
import type { ReportLine } from './api';
import { formatReportValue } from './report-meta';

/** The change on one line: "↑ 11.11%", "↓ 3.84 pts", "— 0"; green or red only for good or bad news. */
export function ReportChange({ line, className }: { line: ReportLine; className?: string }) {
  if (line.change === null) return <span className={cn('text-xs text-muted-foreground', className)}>—</span>;
  const amount =
    line.unit === 'PERCENT'
      ? `${Math.abs(line.change).toFixed(2)} pts`
      : line.changePct !== null
        ? `${formatPercent(Math.abs(line.changePct))}`
        : formatReportValue(Math.abs(line.change), line.unit);
  const better = line.better === 'NEITHER' ? null : line.better === 'HIGHER' ? 'higher' : 'lower';
  return <Delta value={line.change} amount={line.change === 0 ? '0' : amount} better={better} className={className} />;
}
