import { Armchair } from 'lucide-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { useBench } from './api';
import { AllocationMeter, BenchErrorState, ConsultantCell, ConsultantStatusBadge, SkillChips } from './BenchBadges';
import { formatBenchDate, formatDays } from './bench-meta';
import { BenchSection } from './BenchSection';

const SORTS = [
  { value: 'days,desc', label: 'Longest on bench' },
  { value: 'days,asc', label: 'Newest on bench' },
  { value: 'allocated,asc', label: 'Least allocated' },
  { value: 'name,asc', label: 'Name' },
];

/** Active consultants below 100% allocation today, longest on the bench first. Computed by the server, never stored. */
export default function BenchListPage() {
  const [params] = useSearchParams();
  const [search, setSearch] = useState(useInitialSearch());
  const [skill, setSkill] = useState(params.get('skill') ?? '');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());
  const debouncedSkill = useDebouncedValue(skill.trim());
  const bench = useBench({ search: debouncedSearch, skill: debouncedSkill, sort, page, size: 25 });

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <BenchSection title="Bench" description="Everyone available below 100% allocation today, with how long they have been on the bench." />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-44 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name, employee ID or location" aria-label="Search the bench" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Input placeholder="Skill, e.g. Tosca" aria-label="Skill" value={skill} onChange={(e) => filter(setSkill)(e.target.value)} />
          <Select aria-label="Sort the bench" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {bench.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading the bench">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : bench.isError ? (
          <BenchErrorState error={bench.error} title="Couldn't load the bench" onRetry={() => void bench.refetch()} />
        ) : bench.data.content.length === 0 ? (
          <EmptyState
            icon={Armchair}
            title={debouncedSearch || debouncedSkill ? 'Nobody on the bench matches these filters' : 'Nobody is on the bench'}
            description={debouncedSearch || debouncedSkill ? undefined : 'Every available consultant is fully allocated today.'}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Consultant</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Allocated today</TableHead>
                  <TableHead className="text-right">Days on bench</TableHead>
                  <TableHead>On bench since</TableHead>
                  <TableHead>Skills</TableHead>
                  <TableHead>Location</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(bench.isPlaceholderData && 'opacity-60')}>
                {bench.data.content.map((e) => (
                  <TableRow key={e.consultantId}>
                    <TableCell className="max-w-xs">
                      <ConsultantCell consultantId={e.consultantId} name={e.employee?.fullName} user={e.employee?.user} detail={[e.employee?.employeeCode, e.employee?.designation].filter(Boolean).join(' · ')} />
                    </TableCell>
                    <TableCell>
                      <ConsultantStatusBadge status={e.status} />
                    </TableCell>
                    <TableCell>
                      <AllocationMeter percent={e.allocatedPercent} />
                      <span className="block text-xs text-muted-foreground">{e.benchPercent}% free</span>
                    </TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{formatDays(e.daysOnBench)}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{formatBenchDate(e.benchSince)}</TableCell>
                    <TableCell className="max-w-56">
                      <SkillChips skills={e.skills} />
                    </TableCell>
                    <TableCell className="text-sm">{e.location ?? <span className="text-muted-foreground">—</span>}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...bench.data} onPageChange={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
