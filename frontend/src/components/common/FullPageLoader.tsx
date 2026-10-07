import { LoaderCircle } from 'lucide-react';

export function FullPageLoader({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex min-h-svh items-center justify-center bg-background" role="status" aria-live="polite">
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <LoaderCircle className="size-5 animate-spin text-primary" aria-hidden />
        <span>{label}</span>
      </div>
    </div>
  );
}
