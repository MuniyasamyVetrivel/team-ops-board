import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

/**
 * Data table: 52px rows, hairline dividers, muted uppercase headers that stick to the top of a scrolling container
 * (pass a max height through {@code containerClassName}). Mark number columns {@code numeric} on both the header and
 * the cells: they right-align, use tabular figures and never wrap.
 */
export function Table({ className, containerClassName, ...props }: ComponentProps<'table'> & { containerClassName?: string }) {
  return (
    <div className={cn('relative w-full overflow-x-auto', containerClassName)}>
      <table className={cn('w-full caption-bottom text-sm', className)} {...props} />
    </div>
  );
}

export function TableHeader({ className, ...props }: ComponentProps<'thead'>) {
  return <thead className={cn('[&_tr]:border-b', className)} {...props} />;
}

export function TableBody({ className, ...props }: ComponentProps<'tbody'>) {
  return <tbody className={cn('[&_tr:last-child]:border-0', className)} {...props} />;
}

export function TableRow({ className, ...props }: ComponentProps<'tr'>) {
  return (
    <tr
      className={cn(
        'border-b border-border/70 transition-colors hover:bg-muted/40 data-[clickable=true]:cursor-pointer data-[clickable=true]:hover:bg-accent/50',
        className,
      )}
      {...props}
    />
  );
}

type CellProps = { numeric?: boolean };

export function TableHead({ className, numeric, ...props }: ComponentProps<'th'> & CellProps) {
  return (
    <th
      className={cn(
        'sticky top-0 z-10 h-11 bg-card px-4 text-left align-middle text-[11px] font-semibold tracking-[0.06em] whitespace-nowrap text-muted-foreground uppercase first:pl-6 last:pr-6',
        numeric && 'text-right',
        className,
      )}
      {...props}
    />
  );
}

export function TableCell({ className, numeric, ...props }: ComponentProps<'td'> & CellProps) {
  return (
    <td
      className={cn('h-13 px-4 py-2 align-middle first:pl-6 last:pr-6', numeric && 'text-right whitespace-nowrap tabular-nums', className)}
      {...props}
    />
  );
}
