import axios from 'axios';
import { useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

import { errorMessage } from '@/lib/api/errors';

import { exportAllData } from './api';

export type ExportState =
  | { phase: 'idle' }
  | { phase: 'running'; loaded: number }
  | { phase: 'done'; fileName: string; sizeBytes: number }
  | { phase: 'failed'; message: string };

/** One "Export all data" download at a time, with progress and cancel. */
export function useDataExport() {
  const [state, setState] = useState<ExportState>({ phase: 'idle' });
  const controller = useRef<AbortController | null>(null);

  useEffect(() => () => controller.current?.abort(), []);

  const start = async () => {
    controller.current?.abort();
    const abort = new AbortController();
    controller.current = abort;
    setState({ phase: 'running', loaded: 0 });
    try {
      const result = await exportAllData((loaded) => setState({ phase: 'running', loaded }), abort.signal);
      setState({ phase: 'done', ...result });
      toast.success(`Downloaded ${result.fileName}`);
    } catch (error) {
      if (axios.isCancel(error) || abort.signal.aborted) {
        setState({ phase: 'idle' });
        return;
      }
      const message = errorMessage(error, 'The export failed. Please try again.');
      setState({ phase: 'failed', message });
      toast.error(message);
    } finally {
      if (controller.current === abort) controller.current = null;
    }
  };

  const cancel = () => controller.current?.abort();
  const dismiss = () => setState({ phase: 'idle' });

  return { state, start, cancel, dismiss };
}
