import { ChevronDown } from 'lucide-react';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

/**
 * Styled native select: accessible, keyboard-friendly and works with react-hook-form's register().
 * From the sm breakpoint it never truncates: a width set through {@code className} is a minimum, and the box grows
 * to fit its longest option (e.g. "Compare with the previous period").
 */
export function Select({ className, children, ...props }: ComponentProps<'select'>) {
  return (
    <div className={cn('relative w-full sm:min-w-max', className)}>
      <select
        data-slot="select"
        className={cn(
          'h-10 w-full appearance-none rounded-lg border border-input bg-card py-2 pr-9 pl-3 text-sm shadow-xs outline-none transition-[color,box-shadow] sm:w-auto sm:min-w-full',
          'hover:border-gray-400 dark:hover:border-primary/50',
          'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25',
          'aria-invalid:border-destructive disabled:cursor-not-allowed disabled:opacity-50',
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
    </div>
  );
}
