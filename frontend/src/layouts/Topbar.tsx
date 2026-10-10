import { Menu } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { NotificationBell } from '@/features/notifications/NotificationBell';
import { GlobalSearch } from '@/features/search/GlobalSearch';

import { UserMenu } from './UserMenu';

export function Topbar({ onOpenNavigation }: { onOpenNavigation: () => void }) {
  return (
    <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b bg-card/80 px-4 backdrop-blur sm:px-6 lg:px-8">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onOpenNavigation} aria-label="Open navigation">
        <Menu />
      </Button>
      <div className="flex min-w-0 flex-1 justify-start">
        <GlobalSearch />
      </div>
      <NotificationBell />
      <UserMenu />
    </header>
  );
}
