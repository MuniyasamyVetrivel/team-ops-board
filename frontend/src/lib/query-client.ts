import { QueryClient } from '@tanstack/react-query';
import axios from 'axios';

const NON_RETRYABLE_STATUSES = new Set([400, 401, 403, 404, 409, 422]);

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (axios.isAxiosError(error) && NON_RETRYABLE_STATUSES.has(error.response?.status ?? 0)) return false;
        return failureCount < 2;
      },
    },
  },
});
