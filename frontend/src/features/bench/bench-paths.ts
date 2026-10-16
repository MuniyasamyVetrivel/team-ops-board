import { toolPath } from '@/features/modules/module-icons';

/** The module key (BenchModule.KEY); its pages live under /tools/bench. */
export const BENCH_KEY = 'bench';

const ROOT = toolPath(BENCH_KEY);

/** The Bench Management pages, in the order of the module's tab bar. Records open from the URL (?allocation=12). */
export const BENCH_PATHS = {
  dashboard: ROOT,
  bench: `${ROOT}/on-bench`,
  consultants: `${ROOT}/consultants`,
  allocations: `${ROOT}/allocations`,
  engagements: `${ROOT}/engagements`,
  clients: `${ROOT}/clients`,
} as const;

export function consultantPath(id: number): string {
  return `${BENCH_PATHS.consultants}/${id}`;
}

export function allocationHref(id: number): string {
  return `${BENCH_PATHS.allocations}?allocation=${id}`;
}

export function engagementHref(id: number): string {
  return `${BENCH_PATHS.engagements}?engagement=${id}`;
}

export function clientHref(id: number): string {
  return `${BENCH_PATHS.clients}?client=${id}`;
}

/** A list page opened with a starting search (global search's "see all"). */
export function withSearch(path: string, query: string): string {
  return `${path}?search=${encodeURIComponent(query.trim())}`;
}
