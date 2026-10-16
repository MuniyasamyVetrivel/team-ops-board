import { toast } from 'sonner';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { api } from '@/lib/api/client';
import { mockApi } from '@/test/render';

import { toastDeleted, UNDO_TOAST_MS } from './undo-toast';

const originalAdapter = api.defaults.adapter;

describe('toastDeleted', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  it('offers Undo for a few seconds, which restores the record through the undo endpoint', async () => {
    const success = vi.spyOn(toast, 'success').mockReturnValue(1);
    let body: unknown = null;
    mockApi({
      'POST /recycle-bin/undo': (config) => {
        body = JSON.parse(config.data as string);
        return { entityType: 'EMPLOYEE', entityId: 7, label: 'Priya Menon' };
      },
    });

    toastDeleted('Employee deleted', 'EMPLOYEE', 7);

    const [message, options] = success.mock.calls[0];
    expect(message).toBe('Employee deleted');
    expect(options?.duration).toBe(UNDO_TOAST_MS);
    const action = options?.action as { label: string; onClick: () => void };
    expect(action.label).toBe('Undo');

    action.onClick();
    await vi.waitFor(() => expect(success).toHaveBeenCalledWith('Priya Menon restored'));
    expect(body).toEqual({ entityType: 'EMPLOYEE', entityId: 7 });
  });
});
