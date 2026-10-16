import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { cn, initials } from '@/lib/utils';

import { useUserPhoto } from '@/features/profile-photo/api';

const SIZES = { sm: 'size-7 text-[11px]', md: 'size-8', lg: 'size-14 text-lg', xl: 'size-24 text-2xl' } as const;

/** Who the avatar shows: any user reference with an id (and its photo version, when it has a photo). */
export interface AvatarUser {
  id: number;
  photoVersion?: number | null;
}

/**
 * One object URL per photo blob, reused by every avatar of that person. Not revoked: a photo version never changes,
 * and a session only ever sees a few dozen of them.
 */
const objectUrls = new WeakMap<Blob, string>();

function urlFor(blob: Blob | undefined): string | undefined {
  if (!blob || typeof URL.createObjectURL !== 'function') return undefined;
  let url = objectUrls.get(blob);
  if (!url) {
    url = URL.createObjectURL(blob);
    objectUrls.set(blob, url);
  }
  return url;
}

/** The profile photo when there is one (fetched with the login token), otherwise the person's initials. */
export function UserAvatar({ name, user, size = 'md', className }: { name: string; user?: AvatarUser | null; size?: keyof typeof SIZES; className?: string }) {
  const photo = useUserPhoto(user?.id, user?.photoVersion);
  const src = urlFor(photo.data);
  return (
    <Avatar className={cn(SIZES[size], className)}>
      {src && <AvatarImage src={src} alt={name} className="object-cover" />}
      <AvatarFallback className={size === 'lg' || size === 'xl' ? SIZES[size].split(' ').slice(1).join(' ') : undefined}>{initials(name)}</AvatarFallback>
    </Avatar>
  );
}

/** Avatar + name + secondary line, for table cells and lists. */
export function UserCell({ name, detail, user }: { name: string; detail?: string | null; user?: AvatarUser | null }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <UserAvatar name={name} user={user} />
      <div className="min-w-0">
        <p className="truncate font-medium">{name}</p>
        {detail && <p className="truncate text-xs text-muted-foreground">{detail}</p>}
      </div>
    </div>
  );
}
