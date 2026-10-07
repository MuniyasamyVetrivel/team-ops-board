import { cn } from '@/lib/utils';

export function BrandMark({ className, inverted = false }: { className?: string; inverted?: boolean }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <img src="/favicon.svg" alt="" className="size-8" />
      <div className="leading-tight">
        <p className={cn('text-sm font-semibold', inverted ? 'text-white' : 'text-foreground')}>Team Ops Board</p>
        <p className={cn('text-[11px]', inverted ? 'text-indigo-200' : 'text-muted-foreground')}>
          Work &amp; Performance
        </p>
      </div>
    </div>
  );
}
