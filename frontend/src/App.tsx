import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import { Toaster } from 'sonner';

import { queryClient } from '@/lib/query-client';
import { useTheme } from '@/lib/theme';
import { ThemeProvider } from '@/lib/ThemeProvider';
import { router } from '@/routes/router';

function ThemedToaster() {
  const { resolved } = useTheme();
  return <Toaster richColors theme={resolved} position="top-right" />;
}

export default function App() {
  return (
    <ThemeProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <ThemedToaster />
      </QueryClientProvider>
    </ThemeProvider>
  );
}
