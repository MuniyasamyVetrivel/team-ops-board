import { createContext, useContext } from 'react';

import type { DepartmentTool } from './api';

/** The department tool as /api/department-tools lists it for the signed-in user, provided by ToolLayout. */
export const ToolContext = createContext<DepartmentTool | null>(null);

/** The tool and whether the user may change things (its EDIT permission); edit controls are hidden without it. */
export function useTool(): DepartmentTool {
  const tool = useContext(ToolContext);
  if (!tool) throw new Error('useTool must be used inside ToolLayout');
  return tool;
}
