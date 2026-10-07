import { CircleAlert } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { errorMessage } from '@/lib/api/errors';

import { EmptyState } from './EmptyState';

export function ErrorState({ error, onRetry, title = "Couldn't load this" }: { error: unknown; onRetry?: () => void; title?: string }) {
  return (
    <EmptyState
      icon={CircleAlert}
      title={title}
      description={errorMessage(error)}
      action={
        onRetry && (
          <Button variant="outline" onClick={onRetry}>
            Try again
          </Button>
        )
      }
    />
  );
}
