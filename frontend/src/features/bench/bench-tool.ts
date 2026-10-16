import { createContext, useContext } from 'react';

import type { DepartmentTool } from '@/features/modules/api';

/** The Bench Management tool as /api/department-tools lists it for the signed-in user, provided by BenchLayout. */
export const BenchToolContext = createContext<DepartmentTool | null>(null);

/** The tool and whether the user may change things (BENCH_EDIT); edit controls are hidden without it. */
export function useBenchTool(): DepartmentTool {
  const tool = useContext(BenchToolContext);
  if (!tool) throw new Error('useBenchTool must be used inside BenchLayout');
  return tool;
}
