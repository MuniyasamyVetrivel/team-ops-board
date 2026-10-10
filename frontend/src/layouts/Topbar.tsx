import { Menu } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { GlobalSearch } from '@/features/search/GlobalSearch';

import { ThemeMenu } from './ThemeMenu';
import { UserMenu } from './UserMenu';

/** White (navy surface in dark mode) top bar: search centred, icon buttons and the user menu on the right. */
export function Topbar({ onOpenNavigation }: { onOpenNavigation: () => void }) {
  return (
    <header className="sticky top-0 z-30 grid h-16 shrink-0 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 border-b bg-card/90 px-4 backdrop-blur-md sm:px-6 lg:grid-cols-[1fr_minmax(0,36rem)_1fr] lg:px-8">
      <div className="flex items-center">
        <Button variant="ghost" size="icon" className="text-muted-foreground lg:hidden" onClick={onOpenNavigation} aria-label="Open navigation">
          <Menu />
        </Button>
      </div>
      <div className="flex min-w-0 justify-center">
        <GlobalSearch />
      </div>
      <div className="flex items-center justify-end gap-1">
        <ThemeMenu />
        <NotificationBell />
        <span className="mx-2 hidden h-6 w-px bg-border sm:block" aria-hidden />
        <UserMenu />
      </div>
    </header>
  );
}
