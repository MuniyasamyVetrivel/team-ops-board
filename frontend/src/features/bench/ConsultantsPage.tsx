import { FileUp, Plus, UsersRound } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ExportCsvButton } from '@/features/celebrations/CsvButtons';
import { CsvImportDialog } from '@/features/marketing/components/CsvImportDialog';
import { useDebouncedValue } from '@/lib/use-debounced-value';
import { useInitialSearch } from '@/lib/use-initial-search';
import { cn } from '@/lib/utils';

import { BENCH_IMPORTS, CONSULTANT_STATUSES, exportConsultants, useConsultantImportDefinition, useConsultants, type ConsultantQuery, type ConsultantStatus } from './api';
import { AllocationMeter, BenchErrorState, ConsultantCell, ConsultantStatusBadge, SkillChips } from './BenchBadges';
import { CONSULTANT_STATUS_META, formatBenchDate } from './bench-meta';
import { consultantPath } from './bench-paths';
import { useBenchTool } from './bench-tool';
import { BenchSection } from './BenchSection';
import { ConsultantFormDialog } from './ConsultantFormDialog';

const SORTS = [
  { value: 'name,asc', label: 'Name' },
  { value: 'code,asc', label: 'Employee ID' },
  { value: 'available,asc', label: 'Available soonest' },
  { value: 'experience,desc', label: 'Most experienced' },
  { value: 'updated,desc', label: 'Recently updated' },
];

/** Consultants: Employee records with bench details. BENCH_EDIT holders add, import and edit; everyone here can export. */
export default function ConsultantsPage() {
  const { canEdit } = useBenchTool();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [importing, setImporting] = useState(false);
  const [search, setSearch] = useState(useInitialSearch());
  const [status, setStatus] = useState<ConsultantStatus | ''>('');
  const [skill, setSkill] = useState('');
  const [sort, setSort] = useState(SORTS[0]!.value);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search.trim());
  const debouncedSkill = useDebouncedValue(skill.trim());
  const importDefinition = useConsultantImportDefinition(canEdit);

  const filters: Omit<ConsultantQuery, 'page' | 'size' | 'sort'> = { search: debouncedSearch, status: status || undefined, skill: debouncedSkill };
  const consultants = useConsultants({ ...filters, sort, page, size: 25 });
  const filtered = Boolean(debouncedSearch || status || debouncedSkill);

  function filter<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <BenchSection
        title="Consultants"
        description="Skills, certifications, availability and today's allocation for everyone on the books."
        actions={
          <>
            {canEdit && importDefinition.data && (
              <Button variant="outline" onClick={() => setImporting(true)}>
                <FileUp aria-hidden />
                Import CSV
              </Button>
            )}
            <ExportCsvButton onExport={() => exportConsultants(filters)} disabled={!consultants.data?.totalElements} />
            {canEdit && (
              <Button onClick={() => setAdding(true)}>
                <Plus aria-hidden />
                Add consultant
              </Button>
            )}
          </>
        }
      />
      <Card>
        <div className="flex flex-col gap-3 border-b px-6 py-4 sm:flex-row sm:flex-wrap sm:items-center sm:*:w-auto! sm:*:min-w-44 sm:[&>*:first-child]:min-w-64 sm:[&>*:first-child]:flex-1">
          <SearchInput placeholder="Search name, employee ID or location" aria-label="Search consultants" value={search} onChange={(e) => filter(setSearch)(e.target.value)} />
          <Select aria-label="Status" value={status} onChange={(e) => filter(setStatus)(e.target.value as ConsultantStatus | '')}>
            <option value="">All statuses</option>
            {CONSULTANT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {CONSULTANT_STATUS_META[s].label}
              </option>
            ))}
          </Select>
          <Input placeholder="Skill, e.g. Tosca" aria-label="Skill" value={skill} onChange={(e) => filter(setSkill)(e.target.value)} />
          <Select aria-label="Sort consultants" value={sort} onChange={(e) => filter(setSort)(e.target.value)}>
            {SORTS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Select>
        </div>

        {consultants.isPending ? (
          <div className="space-y-3 p-4" role="status" aria-label="Loading consultants">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : consultants.isError ? (
          <BenchErrorState error={consultants.error} title="Couldn't load consultants" onRetry={() => void consultants.refetch()} />
        ) : consultants.data.content.length === 0 ? (
          <EmptyState
            icon={UsersRound}
            title={filtered ? 'No consultants match these filters' : 'No consultants yet'}
            description={!filtered && canEdit ? 'Add consultants from Employee records one by one, or import a CSV.' : undefined}
          />
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Consultant</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Allocated today</TableHead>
                  <TableHead>Skills</TableHead>
                  <TableHead className="text-right">Experience</TableHead>
                  <TableHead>Location</TableHead>
                  <TableHead>Available from</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className={cn(consultants.isPlaceholderData && 'opacity-60')}>
                {consultants.data.content.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="max-w-xs">
                      <ConsultantCell consultantId={c.id} name={c.employee?.fullName} user={c.employee?.user} detail={[c.employee?.employeeCode, c.employee?.designation].filter(Boolean).join(' · ')} />
                    </TableCell>
                    <TableCell>
                      <ConsultantStatusBadge status={c.status} />
                    </TableCell>
                    <TableCell>
                      <AllocationMeter percent={c.allocatedPercent} />
                    </TableCell>
                    <TableCell className="max-w-56">
                      <SkillChips skills={c.skills} />
                    </TableCell>
                    <TableCell className="text-right text-sm tabular-nums">{c.experienceYears === null ? '—' : `${c.experienceYears} yrs`}</TableCell>
                    <TableCell className="text-sm">{c.location ?? <span className="text-muted-foreground">—</span>}</TableCell>
                    <TableCell className="text-sm whitespace-nowrap">{formatBenchDate(c.availableFrom)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Pagination {...consultants.data} onPageChange={setPage} />
          </>
        )}
      </Card>
      {canEdit && <ConsultantFormDialog open={adding} onOpenChange={setAdding} onSaved={(id) => void navigate(consultantPath(id))} />}
      {importDefinition.data && <CsvImportDialog definition={importDefinition.data} endpoint={BENCH_IMPORTS} open={importing} onOpenChange={setImporting} />}
    </div>
  );
}
