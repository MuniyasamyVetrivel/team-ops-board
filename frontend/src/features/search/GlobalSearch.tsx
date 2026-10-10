import {
  BookOpen,
  ClipboardList,
  FileSearch,
  FolderKanban,
  KeyRound,
  LifeBuoy,
  Mail,
  MousePointerClick,
  Search,
  UserPlus,
  UserRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router';

import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/api/errors';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

import { groupHref, hitHref, MIN_QUERY_LENGTH, statusLabel, useGlobalSearch, type ResultType, type SearchHit } from './api';

const TYPE_META: Record<ResultType, { icon: LucideIcon; label: string }> = {
  TASK: { icon: ClipboardList, label: 'Task' },
  TICKET: { icon: LifeBuoy, label: 'Ticket' },
  PROJECT: { icon: FolderKanban, label: 'Project' },
  EMPLOYEE: { icon: UserRound, label: 'Employee' },
  ARTICLE: { icon: BookOpen, label: 'Article' },
  MARKETING_PAGE: { icon: FileSearch, label: 'Marketing page' },
  KEYWORD: { icon: KeyRound, label: 'Keyword' },
  EMAIL_CAMPAIGN: { icon: Mail, label: 'Email campaign' },
  PAID_CAMPAIGN: { icon: MousePointerClick, label: 'Paid campaign' },
  LEAD: { icon: UserPlus, label: 'Lead' },
};

/**
 * Global search in the top bar (Ctrl/⌘ K). Searches every module the viewer may see; the server decides which
 * groups and records they get. Arrow keys move through the results, Enter opens one, Escape closes.
 */
export function GlobalSearch() {
  const navigate = useNavigate();
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = useId();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const debounced = useDebouncedValue(query.trim(), 250);
  const results = useGlobalSearch(debounced);
  const ready = debounced.length >= MIN_QUERY_LENGTH;
  const groups = useMemo(() => (ready ? (results.data?.groups ?? []) : []), [ready, results.data]);
  const hits = useMemo(() => groups.flatMap((g) => g.hits), [groups]);
  // Position of each group's first hit in the flat list the arrow keys move through.
  const offsets = useMemo(() => groups.map((_, g) => groups.slice(0, g).reduce((sum, prev) => sum + prev.hits.length, 0)), [groups]);

  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const go = (href: string) => {
    setOpen(false);
    setQuery('');
    inputRef.current?.blur();
    void navigate(href);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      setOpen(false);
      inputRef.current?.blur();
      return;
    }
    if (!open) setOpen(true);
    if (hits.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((i) => (i + 1) % hits.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => (i - 1 + hits.length) % hits.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const hit = hits[Math.min(active, hits.length - 1)];
      if (hit) go(hitHref(hit));
    }
  };

  const showPanel = open && query.trim().length > 0;
  const optionId = (index: number) => `${listId}-option-${index}`;

  return (
    <div className="relative w-full max-w-md">
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
      <input
        ref={inputRef}
        type="search"
        role="combobox"
        aria-label="Search everything"
        aria-expanded={showPanel}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showPanel && hits.length > 0 ? optionId(Math.min(active, hits.length - 1)) : undefined}
        placeholder="Search tasks, tickets, people, leads…"
        className="h-9 w-full rounded-md border border-input bg-card pr-14 pl-9 text-sm shadow-xs outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/30"
        value={query}
        onChange={(event) => {
          setQuery(event.target.value);
          setActive(0);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
      />
      <kbd className="pointer-events-none absolute top-1/2 right-2 hidden -translate-y-1/2 rounded border bg-muted px-1.5 font-mono text-[10px] text-muted-foreground sm:block" aria-hidden>
        Ctrl K
      </kbd>

      {showPanel && (
        <div
          className="absolute top-full right-0 left-0 z-50 mt-1.5 max-h-[70vh] overflow-y-auto rounded-lg border bg-card shadow-lg sm:min-w-[28rem]"
          // Keep focus in the input while clicking a result.
          onMouseDown={(event) => event.preventDefault()}
        >
          {!ready ? (
            <p className="px-4 py-3 text-sm text-muted-foreground">Type at least {MIN_QUERY_LENGTH} characters to search.</p>
          ) : results.isError ? (
            <p className="px-4 py-3 text-sm text-destructive" role="alert">
              {errorMessage(results.error, "Search isn't available right now.")}
            </p>
          ) : !results.data || (results.isFetching && results.data.query !== debounced && hits.length === 0) ? (
            <div className="space-y-2 p-3" role="status" aria-label="Searching">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="h-9 rounded-md" />
              ))}
            </div>
          ) : groups.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-muted-foreground">
              No matches for <span className="font-medium text-foreground">“{debounced}”</span> in anything you can see.
            </p>
          ) : (
            <ul id={listId} role="listbox" aria-label="Search results" className={cn('py-1', results.isFetching && 'opacity-70')}>
              {groups.map((group, g) => (
                <li key={group.group} role="presentation">
                  <div className="flex items-center justify-between px-4 pt-2 pb-1">
                    <span className="text-[11px] font-semibold tracking-wider text-muted-foreground uppercase">{group.label}</span>
                    {group.total > group.hits.length && (
                      <button type="button" className="text-xs font-medium text-primary hover:underline" onClick={() => go(groupHref(group.group, debounced))}>
                        See all {group.total}
                      </button>
                    )}
                  </div>
                  <ul role="group" aria-label={group.label}>
                    {group.hits.map((hit, h) => {
                      const i = offsets[g]! + h;
                      return <ResultOption key={`${hit.type}-${hit.id}`} hit={hit} id={optionId(i)} active={i === active} onHover={() => setActive(i)} onSelect={() => go(hitHref(hit))} />;
                    })}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function ResultOption({ hit, id, active, onHover, onSelect }: { hit: SearchHit; id: string; active: boolean; onHover: () => void; onSelect: () => void }) {
  const meta = TYPE_META[hit.type];
  return (
    <li
      id={id}
      role="option"
      aria-selected={active}
      className={cn('mx-1 flex cursor-pointer items-center gap-3 rounded-md px-3 py-2', active && 'bg-accent text-accent-foreground')}
      onMouseEnter={onHover}
      onClick={onSelect}
    >
      <meta.icon className="size-4 shrink-0 text-muted-foreground" aria-label={meta.label} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm">
          {hit.code && <span className="mr-1.5 font-mono text-xs text-muted-foreground">{hit.code}</span>}
          {hit.title}
        </p>
        {hit.subtitle && <p className="truncate text-xs text-muted-foreground">{hit.subtitle}</p>}
      </div>
      {hit.status && <Badge tone="neutral">{statusLabel(hit.status)}</Badge>}
    </li>
  );
}
