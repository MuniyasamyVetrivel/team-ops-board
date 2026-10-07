import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

/** Card with a titled header and optional action link. */
export function Panel({
  title,
  icon: Icon,
  description,
  action,
  children,
  className,
}: {
  title: string;
  icon: LucideIcon;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn('flex flex-col', className)}>
      <div className="flex items-start justify-between gap-3 border-b px-5 py-3.5">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-sm font-semibold">
            <Icon className="size-4 text-muted-foreground" aria-hidden />
            {title}
          </h2>
          {description && <p className="mt-0.5 text-xs text-muted-foreground">{description}</p>}
        </div>
        {action}
      </div>
      <div className="flex-1">{children}</div>
    </Card>
  );
}

export function PanelLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="shrink-0 text-xs font-medium text-primary hover:underline">
      {children}
    </Link>
  );
}
