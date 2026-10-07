import { X } from 'lucide-react';
import { useMemo } from 'react';
import { NavLink } from 'react-router';

import { Button } from '@/components/ui/button';
import { visibleNavigation } from '@/config/navigation';
import { useAuth } from '@/features/auth/use-auth';
import { cn } from '@/lib/utils';

import { BrandMark } from './BrandMark';

interface SidebarProps {
  mobileOpen: boolean;
  onNavigate: () => void;
}

export function Sidebar({ mobileOpen, onNavigate }: SidebarProps) {
  const { user } = useAuth();
  const sections = useMemo(() => (user ? visibleNavigation(user) : []), [user]);

  return (
    <>
      {mobileOpen && (
        <div className="fixed inset-0 z-40 bg-black/40 lg:hidden" aria-hidden onClick={onNavigate} />
      )}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r bg-sidebar text-sidebar-foreground transition-transform lg:sticky lg:top-0 lg:h-svh lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
        aria-label="Main navigation"
      >
        <div className="flex h-16 shrink-0 items-center justify-between border-b px-5">
          <BrandMark />
          <Button variant="ghost" size="icon" className="lg:hidden" onClick={onNavigate} aria-label="Close navigation">
            <X />
          </Button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          {sections.map((section) => (
            <div key={section.id} className="mb-5 last:mb-0">
              {section.label && (
                <p className="mb-1.5 px-3 text-[11px] font-semibold tracking-wider text-muted-foreground uppercase">
                  {section.label}
                </p>
              )}
              <ul className="space-y-0.5">
                {section.items.map((item) => (
                  <li key={item.path}>
                    <NavLink
                      to={item.path}
                      end={item.path === '/digital-marketing'}
                      onClick={onNavigate}
                      className={({ isActive }) =>
                        cn(
                          'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                          isActive
                            ? 'bg-accent text-accent-foreground'
                            : 'hover:bg-muted hover:text-foreground',
                        )
                      }
                    >
                      <item.icon className="size-4 shrink-0" aria-hidden />
                      <span className="truncate">{item.label}</span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
    </>
  );
}
