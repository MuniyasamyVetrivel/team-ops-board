import { Check, Search, X } from 'lucide-react';
import { useId, useState } from 'react';

import { UserAvatar } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useTeamDirectory } from '@/features/team/api';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { cn } from '@/lib/utils';

/** A person as the picker shows them; licences send only the ids. */
export interface PickedPerson {
  id: number;
  fullName: string;
  detail?: string | null;
  photoVersion?: number | null;
}

interface PeoplePickerProps {
  id: string;
  value: PickedPerson[];
  onChange: (people: PickedPerson[]) => void;
  /** One person (an owner) or many (a licence's assigned users). */
  multiple?: boolean;
  placeholder?: string;
  invalid?: boolean;
  disabled?: boolean;
}

/**
 * Searches active people in the team directory (TEAM_VIEW, on every role) as you type and picks one or many. The
 * server checks again that every new owner or assignee is active (USER_INACTIVE).
 */
export function PeoplePicker({ id, value, onChange, multiple = false, placeholder = 'Search people by name or email', invalid, disabled }: PeoplePickerProps) {
  const listId = useId();
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const search = useDebouncedValue(query.trim());
  const results = useTeamDirectory({ status: 'ACTIVE', search: search || undefined, size: 8, sort: 'name,asc' }, open && search.length > 0);
  const picked = new Set(value.map((p) => p.id));

  function toggle(person: PickedPerson) {
    if (picked.has(person.id)) onChange(value.filter((p) => p.id !== person.id));
    else onChange(multiple ? [...value, person] : [person]);
    if (!multiple) {
      setQuery('');
      setOpen(false);
    }
  }

  return (
    <div className="space-y-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label={multiple ? 'Chosen people' : 'Chosen person'}>
          {value.map((p) => (
            <li key={p.id} className="inline-flex items-center gap-1.5 rounded-full border bg-muted/40 py-0.5 pr-1 pl-0.5 text-sm">
              <UserAvatar name={p.fullName} user={{ id: p.id, photoVersion: p.photoVersion ?? null }} size="sm" className="size-5 text-[9px]" />
              <span className="max-w-48 truncate">{p.fullName}</span>
              {!disabled && (
                <button
                  type="button"
                  className="rounded-full p-0.5 text-muted-foreground outline-none hover:bg-muted hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring/50"
                  aria-label={`Remove ${p.fullName}`}
                  onClick={() => onChange(value.filter((v) => v.id !== p.id))}
                >
                  <X className="size-3.5" aria-hidden />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {(multiple || value.length === 0) && !disabled && (
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
          <Input
            id={id}
            role="combobox"
            aria-expanded={open && search.length > 0}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-invalid={invalid}
            className="pl-9"
            placeholder={placeholder}
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onBlur={() => setTimeout(() => setOpen(false), 150)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setOpen(false);
            }}
          />
          {open && search.length > 0 && (
            <ul id={listId} role="listbox" aria-label="Matching people" className="absolute inset-x-0 top-full z-50 mt-1 max-h-64 overflow-y-auto rounded-lg border bg-popover p-1 shadow-popover">
              {results.isPending ? (
                <li className="px-3 py-2 text-sm text-muted-foreground">Searching…</li>
              ) : results.isError ? (
                <li className="px-3 py-2 text-sm text-status-danger">Couldn't search people</li>
              ) : results.data.content.length === 0 ? (
                <li className="px-3 py-2 text-sm text-muted-foreground">No active people match</li>
              ) : (
                results.data.content.map((m) => {
                  const selected = picked.has(m.id);
                  return (
                    <li key={m.id} role="option" aria-selected={selected}>
                      <Button
                        type="button"
                        variant="ghost"
                        className={cn('h-auto w-full justify-start gap-2.5 px-2 py-1.5 text-left font-normal', selected && 'bg-accent')}
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => toggle({ id: m.id, fullName: m.fullName, detail: m.department.name, photoVersion: m.photoVersion })}
                      >
                        <UserAvatar name={m.fullName} user={m} size="sm" />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-sm">{m.fullName}</span>
                          <span className="block truncate text-xs text-muted-foreground">
                            {m.department.name}
                            {m.jobTitle ? ` · ${m.jobTitle}` : ''}
                          </span>
                        </span>
                        {selected && <Check className="size-4 text-primary" aria-label="Chosen" />}
                      </Button>
                    </li>
                  );
                })
              )}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
