import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';

import { cn } from '@/lib/utils';

/**
 * Soft pill. Tones follow the semantic status system (brief section 62); pair with a label or icon, never colour alone.
 * {@code highlight} is the brand amber accent (navy text on amber), for emphasis rather than status.
 */
export const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs leading-5 font-medium whitespace-nowrap [&_svg:not([class*='size-'])]:size-3.5",
  {
    variants: {
      tone: {
        neutral: 'border-transparent bg-muted text-muted-foreground dark:border-border',
        primary: 'border-transparent bg-accent text-accent-foreground',
        success: 'border-transparent bg-status-success/10 text-status-success dark:border-status-success/25',
        warning: 'border-transparent bg-status-warning/10 text-status-warning dark:border-status-warning/25',
        danger: 'border-transparent bg-status-danger/10 text-status-danger dark:border-status-danger/25',
        highlight: 'border-transparent bg-highlight text-highlight-foreground',
      },
    },
    defaultVariants: { tone: 'neutral' },
  },
);

export type BadgeProps = ComponentProps<'span'> & VariantProps<typeof badgeVariants>;

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span data-slot="badge" className={cn(badgeVariants({ tone }), className)} {...props} />;
}
