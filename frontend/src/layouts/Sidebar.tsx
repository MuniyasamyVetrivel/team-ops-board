import { X } from 'lucide-react';
import { useMemo } from 'react';
import { NavLink } from 'react-router';

import { visibleNavigation } from '@/config/navigation';
import { useAuth } from '@/features/auth/use-auth';
import { cn } from '@/lib/utils';

import { BrandMark } from './BrandMark';

interface SidebarProps {
  mobileOpen: boolean;
  onNavigate: () => void;
}

/** Navy sidebar. The active item is brand blue with a small amber indicator on its left edge. */
export function Sidebar({ mobileOpen, onNavigate }: SidebarProps) {
  const { user } = useAuth();
  const sections = useMemo(() => (user ? visibleNavigation(user) : []), [user]);

  return (
    <>
      {mobileOpen && <div className="fixed inset-0 z-40 bg-navy-950/50 backdrop-blur-[2px] lg:hidden" aria-hidden onClick={onNavigate} />}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground transition-transform lg:sticky lg:top-0 lg:h-svh lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
        aria-label="Main navigation"
      >
        <div className="flex h-16 shrink-0 items-center justify-between px-5">
          <BrandMark inverted />
          <button
            type="button"
            className="flex size-9 items-center justify-center rounded-lg text-sidebar-foreground outline-none hover:bg-sidebar-hover hover:text-white focus-visible:ring-2 focus-visible:ring-white/40 lg:hidden"
            onClick={onNavigate}
            aria-label="Close navigation"
          >
            <X className="size-5" aria-hidden />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 pt-4 pb-6 [scrollbar-color:var(--color-navy-700)_transparent] [scrollbar-width:thin]">
          {sections.map((section) => (
            <div key={section.id} className="mb-6 last:mb-0">
              {section.label && <p className="mb-2 px-3 text-[11px] font-semibold tracking-[0.08em] text-sidebar-muted uppercase">{section.label}</p>}
              <ul className="space-y-0.5">
                {section.items.map((item) => (
                  <li key={item.path}>
                    <NavLink
                      to={item.path}
                      end={item.path === '/digital-marketing'}
                      onClick={onNavigate}
                      className={({ isActive }) =>
                        cn(
                          'relative flex h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors outline-none focus-visible:ring-2 focus-visible:ring-white/40',
                          isActive ? 'bg-sidebar-active text-white shadow-sm' : 'hover:bg-sidebar-hover hover:text-white',
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && <span className="absolute top-2 bottom-2 -left-3 w-1 rounded-r-full bg-highlight" aria-hidden />}
                          <item.icon className={cn('size-[18px] shrink-0', isActive ? 'text-white' : 'text-sidebar-muted')} aria-hidden />
                          <span className="truncate">{item.label}</span>
                        </>
                      )}
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
