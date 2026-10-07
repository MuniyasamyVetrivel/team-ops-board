import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import { Toaster } from 'sonner';

import { queryClient } from '@/lib/query-client';
import { router } from '@/routes/router';

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster richColors position="top-right" />
    </QueryClientProvider>
  );
}
