import { toast } from 'sonner';

import { errorMessage } from '@/lib/api/errors';
import { queryClient } from '@/lib/query-client';

import type { HistoryRecordType } from '@/features/history/api';

import { undoDelete } from './api';

/** How long the "Undo" button stays (the server allows the undo for two minutes). */
export const UNDO_TOAST_MS = 8_000;

/**
 * The toast after deleting a record: deleted items go to the recycle bin, so whoever deleted one can bring it straight
 * back with "Undo". Every query is refetched afterwards, so the record reappears wherever it was listed.
 */
export function toastDeleted(message: string, entityType: HistoryRecordType, entityId: number): void {
  toast.success(message, {
    description: 'Moved to the recycle bin.',
    duration: UNDO_TOAST_MS,
    action: {
      label: 'Undo',
      onClick: () => {
        undoDelete(entityType, entityId)
          .then(async (restored) => {
            await queryClient.invalidateQueries();
            toast.success(restored.label ? `${restored.label} restored` : 'Restored');
          })
          .catch((error: unknown) => toast.error(errorMessage(error, "Couldn't undo the delete")));
      },
    },
  });
}
