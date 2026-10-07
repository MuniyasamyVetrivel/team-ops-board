import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { cn, initials } from '@/lib/utils';

const SIZES = { sm: 'size-7 text-[11px]', md: 'size-8', lg: 'size-14 text-lg' } as const;

export function UserAvatar({ name, size = 'md', className }: { name: string; size?: keyof typeof SIZES; className?: string }) {
  return (
    <Avatar className={cn(SIZES[size], className)}>
      <AvatarFallback className={size === 'lg' ? 'text-lg' : undefined}>{initials(name)}</AvatarFallback>
    </Avatar>
  );
}

/** Avatar + name + secondary line, for table cells and lists. */
export function UserCell({ name, detail }: { name: string; detail?: string | null }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <UserAvatar name={name} />
      <div className="min-w-0">
        <p className="truncate font-medium">{name}</p>
        {detail && <p className="truncate text-xs text-muted-foreground">{detail}</p>}
      </div>
    </div>
  );
}
