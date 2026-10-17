import { ToolLayout, type ToolTab } from '@/features/modules/ToolLayout';

import { BENCH_KEY, BENCH_PATHS } from './bench-paths';

const TABS: ToolTab[] = [
  { to: BENCH_PATHS.dashboard, label: 'Dashboard', end: true },
  { to: BENCH_PATHS.bench, label: 'Bench' },
  { to: BENCH_PATHS.consultants, label: 'Consultants' },
  { to: BENCH_PATHS.allocations, label: 'Allocations' },
  { to: BENCH_PATHS.engagements, label: 'Engagements' },
  { to: BENCH_PATHS.clients, label: 'Clients' },
];

/** Wraps every /tools/bench page (the shared department tool layout); pages read `canEdit` from useBenchTool. */
export function BenchLayout() {
  return <ToolLayout toolKey={BENCH_KEY} tabs={TABS} />;
}
