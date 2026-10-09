import { formatCount, formatDecimal, formatInr, formatPercent } from '../marketing-format';
import type { GroupKey, MonthlyReport, ReportLine, ReportUnit } from './api';

/** A report value in its unit: "1,250", "35.42%", "₹42,000", "9.4"; "—" when missing. */
export function formatReportValue(value: number | null, unit: ReportUnit): string {
  switch (unit) {
    case 'PERCENT':
      return formatPercent(value);
    case 'CURRENCY':
      return formatInr(value);
    case 'DECIMAL':
      return formatDecimal(value);
    default:
      return formatCount(value);
  }
}

/** Brief section 50's headline figures, e.g. "Leads 180 → 200 +11.1%", in the order they are shown. */
export const HEADLINES: { group: GroupKey; label: string; title: string }[] = [
  { group: 'LEADS', label: 'Leads generated', title: 'Leads' },
  { group: 'SEO', label: 'Top 10 keywords', title: 'Top 10 keywords' },
  { group: 'BACKLINKS', label: 'Live', title: 'Backlinks live' },
  { group: 'CONTENT', label: 'Blogs published', title: 'Blogs published' },
  { group: 'LINKEDIN', label: 'Leads', title: 'LinkedIn leads' },
  { group: 'EMAIL', label: 'Open rate', title: 'Email open rate' },
];

/** The headline lines present in the report (a group the viewer may not see is simply missing). */
export function headlines(report: MonthlyReport): { title: string; line: ReportLine }[] {
  return HEADLINES.flatMap((h) => {
    const line = report.groups.find((g) => g.key === h.group)?.lines.find((l) => l.label === h.label);
    return line ? [{ title: h.title, line }] : [];
  });
}
