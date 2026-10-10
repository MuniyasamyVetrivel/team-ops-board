import type { LucideIcon } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import { Card } from '@/components/ui/card';
import { cn } from '@/lib/utils';

/** Card with a titled header and optional action. Fills its grid cell, so panels in one row share a height. */
export function Panel({
  title,
  icon: Icon,
  description,
  action,
  children,
  className,
}: {
  title: string;
  icon?: LucideIcon;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card className={cn('flex min-w-0 flex-col', className)}>
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2 border-b border-border/70 px-6 py-4">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-card-title font-semibold">
            {Icon && <Icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />}
            {title}
          </h2>
          {description && <p className="mt-0.5 text-label text-muted-foreground">{description}</p>}
        </div>
        {action}
      </div>
      <div className="flex-1">{children}</div>
    </Card>
  );
}

export function PanelLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="shrink-0 text-label font-medium whitespace-nowrap text-primary hover:underline">
      {children}
    </Link>
  );
}
