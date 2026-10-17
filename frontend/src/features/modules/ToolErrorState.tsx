import { Ban, ToggleLeft } from 'lucide-react';

import { EmptyState } from '@/components/common/EmptyState';
import { ErrorState } from '@/components/common/ErrorState';
import { toApiError } from '@/lib/api/errors';

/**
 * A department tool's load error. A module switched off since the page opened (403 MODULE_DISABLED) reads as that,
 * and a missing permission as that, not as a generic failure (docs/adding-a-module.md).
 */
export function ToolErrorState({ error, title, onRetry, toolName, permission }: { error: unknown; title: string; onRetry?: () => void; toolName: string; permission: string }) {
  const code = toApiError(error)?.code;
  if (code === 'MODULE_DISABLED') {
    return <EmptyState icon={ToggleLeft} title="This tool is not switched on for your department" description={`Ask your Super Admin to switch ${toolName} on in Admin settings → Modules.`} />;
  }
  if (code === 'FORBIDDEN') {
    return <EmptyState icon={Ban} title="You don't have access to this" description={`${toolName} needs the ${permission} permission.`} />;
  }
  return <ErrorState error={error} title={title} onRetry={onRetry} />;
}
