import { cn } from '@/lib/utils';

/** Logo + product name. {@code inverted} is for navy backgrounds (sidebar, sign-in panel). */
export function BrandMark({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <img src="/favicon.svg" alt="" className="size-8" />
      <div className="leading-tight">
        <p className={cn('font-display text-[15px] font-bold tracking-tight', inverted ? 'text-white' : 'text-foreground')}>Team Ops Board</p>
        <p className={cn('text-[11px] font-medium', inverted ? 'text-sidebar-muted' : 'text-muted-foreground')}>Work &amp; Performance</p>
      </div>
    </div>
  );
}
