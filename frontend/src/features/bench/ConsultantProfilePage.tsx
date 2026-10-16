import { ArrowLeft, Award, BadgeCheck, Briefcase, CalendarDays, CalendarRange, Gauge, MapPin, Pencil, Plus, Sparkles, TriangleAlert, type LucideIcon } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Detail, Muted } from '@/features/celebrations/Details';
import { useCanViewHistory } from '@/features/history/api';
import { RecordHistory } from '@/features/history/RecordHistory';
import { useIdParam } from '@/lib/use-id-param';

import { useAllocations, useBenchDashboard, useConsultant, type Consultant } from './api';
import { AllocationFormDialog } from './AllocationFormDialog';
import { AllocationSheet } from './AllocationSheet';
import { AllocationTimeline } from './AllocationTimeline';
import { AllocationMeter, BenchErrorState, BillableBadge, ConsultantStatusBadge, PeriodStatusBadge } from './BenchBadges';
import { consultantName, dateRange, EMPLOYEE_IN_BIN, EMPLOYMENT_STATUS_LABELS, formatBenchDate } from './bench-meta';
import { BENCH_PATHS } from './bench-paths';
import { useBenchTool } from './bench-tool';
import { ConsultantFormDialog } from './ConsultantFormDialog';

/** One consultant: who they are (from the Employee record), skills, certifications and an allocation timeline. */
export default function ConsultantProfilePage() {
  const { canEdit } = useBenchTool();
  const canViewHistory = useCanViewHistory();
  const navigate = useNavigate();
  const id = Number(useParams().id);
  const consultant = useConsultant(Number.isInteger(id) && id > 0 ? id : null);
  const [openAllocation, setOpenAllocation] = useIdParam('allocation');
  const [editing, setEditing] = useState(false);
  const [allocating, setAllocating] = useState(false);

  if (consultant.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading the consultant">
        <Skeleton className="h-20 w-2/3" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-80 rounded-xl" />
      </div>
    );
  }
  if (consultant.isError) {
    return (
      <Card>
        <BenchErrorState error={consultant.error} title="Couldn't open this consultant" onRetry={() => void consultant.refetch()} />
      </Card>
    );
  }

  const c = consultant.data;
  const name = consultantName(c.employee?.fullName);
  return (
    <div className="space-y-6">
      <Link to={BENCH_PATHS.consultants} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Consultants
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 items-center gap-4">
          <UserAvatar name={c.employee?.fullName ?? '?'} user={c.employee?.user} size="lg" />
          <div className="min-w-0">
            <h2 className="truncate text-xl font-semibold tracking-tight">{name}</h2>
            <p className="truncate text-sm text-muted-foreground">{[c.employee?.employeeCode, c.employee?.designation, c.employee?.department?.name].filter(Boolean).join(' · ')}</p>
            <div className="mt-1.5 flex flex-wrap items-center gap-2">
              <ConsultantStatusBadge status={c.status} />
            </div>
          </div>
        </div>
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            {c.employmentStatus !== 'LEFT' && (
              <Button variant="outline" onClick={() => setAllocating(true)}>
                <Plus aria-hidden />
                Allocate
              </Button>
            )}
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit consultant
            </Button>
          </div>
        )}
      </div>

      {!c.employee && (
        <div role="alert" className="flex items-start gap-3 rounded-lg border border-status-warning/40 bg-status-warning/10 p-4 text-sm">
          <TriangleAlert className="mt-0.5 size-4 shrink-0 text-status-warning" aria-hidden />
          <p>
            <span className="font-medium">{EMPLOYEE_IN_BIN}.</span> This consultant's Employee record is in the recycle bin, so their name and employee ID are hidden until it is restored.
          </p>
        </div>
      )}

      <section aria-label="Consultant figures" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Allocated today" icon={Gauge}>
          <AllocationMeter percent={c.allocatedPercent} />
        </Stat>
        <Stat label="Experience" icon={Briefcase}>
          <span className="text-lg font-semibold tabular-nums">{c.experienceYears === null ? '—' : `${c.experienceYears} years`}</span>
        </Stat>
        <Stat label="Available from" icon={CalendarDays}>
          <span className="text-lg font-semibold">{formatBenchDate(c.availableFrom)}</span>
        </Stat>
        <Stat label="Location" icon={MapPin}>
          <span className="text-lg font-semibold">{c.location ?? '—'}</span>
        </Stat>
      </section>

      <Card>
        <Tabs defaultValue={openAllocation !== null ? 'allocations' : 'overview'} className="pb-4">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="allocations">Allocations</TabsTrigger>
            {canViewHistory && <TabsTrigger value="history">History</TabsTrigger>}
          </TabsList>
          <div className="px-4 pt-5 sm:px-6">
            <TabsContent value="overview">
              <Overview consultant={c} />
            </TabsContent>
            <TabsContent value="allocations">
              <Allocations consultantId={c.id} onOpen={setOpenAllocation} />
            </TabsContent>
            {canViewHistory && (
              <TabsContent value="history" className="pb-2">
                <RecordHistory type="CONSULTANT" id={c.id} />
              </TabsContent>
            )}
          </div>
        </Tabs>
      </Card>

      <AllocationSheet allocationId={openAllocation} onClose={() => setOpenAllocation(null)} />
      {canEdit && (
        <>
          <ConsultantFormDialog open={editing} onOpenChange={setEditing} consultant={c} onDeleted={() => void navigate(BENCH_PATHS.consultants)} />
          <AllocationFormDialog open={allocating} onOpenChange={setAllocating} consultantId={c.id} onSaved={setOpenAllocation} />
        </>
      )}
    </div>
  );
}

function Stat({ label, icon: Icon, children }: { label: string; icon: LucideIcon; children: ReactNode }) {
  return (
    <div className="flex h-full flex-col rounded-xl border bg-card px-4 py-3.5 shadow-card">
      <p className="flex items-center justify-between gap-2 text-label font-medium text-muted-foreground">
        {label}
        <Icon className="size-4" aria-hidden />
      </p>
      <div className="mt-2">{children}</div>
    </div>
  );
}

function Overview({ consultant: c }: { consultant: Consultant }) {
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="space-y-8">
        <section aria-labelledby="skills-heading">
          <h3 id="skills-heading" className="mb-3 flex items-center gap-2 text-sm font-medium">
            <Sparkles className="size-4 text-muted-foreground" aria-hidden />
            Skills
          </h3>
          {c.skills.length === 0 ? (
            <p className="text-sm text-muted-foreground">No skills recorded yet.</p>
          ) : (
            <ul className="flex flex-wrap gap-2">
              {c.skills.map((skill) => (
                <li key={skill}>
                  <Link to={`${BENCH_PATHS.bench}?skill=${encodeURIComponent(skill)}`} className="inline-block rounded-md border bg-muted/50 px-2.5 py-1 text-sm hover:border-primary/40" title="See who is on the bench with this skill">
                    {skill}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="certifications-heading">
          <h3 id="certifications-heading" className="mb-3 flex items-center gap-2 text-sm font-medium">
            <Award className="size-4 text-muted-foreground" aria-hidden />
            Certifications
          </h3>
          {c.certifications.length === 0 ? (
            <p className="text-sm text-muted-foreground">No certifications recorded yet.</p>
          ) : (
            <ul className="divide-y rounded-lg border">
              {c.certifications.map((cert) => (
                <li key={cert.id} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-4 py-3">
                  <BadgeCheck className={cert.expired ? 'mt-0.5 size-4 shrink-0 text-muted-foreground' : 'mt-0.5 size-4 shrink-0 text-status-success'} aria-hidden />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium">{cert.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {[
                        cert.issuer,
                        cert.issuedOn && `Issued ${formatBenchDate(cert.issuedOn)}`,
                        cert.expiresOn && `${cert.expired ? 'Expired' : 'Expires'} ${formatBenchDate(cert.expiresOn)}`,
                        cert.credentialId && `ID ${cert.credentialId}`,
                      ]
                        .filter(Boolean)
                        .join(' · ') || 'No details'}
                    </p>
                  </div>
                  {cert.expired ? <Badge tone="danger">Expired</Badge> : cert.expiresOn ? <Badge tone="success">Valid</Badge> : null}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <dl className="grid content-start gap-x-6 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-1">
        <Detail label="Employment status (HR)">{EMPLOYMENT_STATUS_LABELS[c.employmentStatus]}</Detail>
        <Detail label="Work email">{c.employee?.workEmail ?? <Muted>None</Muted>}</Detail>
        <Detail label="Department">{c.employee?.department?.name ?? <Muted>None</Muted>}</Detail>
        <Detail label="Notes">{c.notes ? <span className="whitespace-pre-wrap">{c.notes}</span> : <Muted>None</Muted>}</Detail>
      </dl>
    </div>
  );
}

function Allocations({ consultantId, onOpen }: { consultantId: number; onOpen: (id: number) => void }) {
  const allocations = useAllocations({ consultantId, size: 100, sort: 'start,asc' });
  // The timeline's "today" is the business date the server computed the dashboard for, never the browser's clock.
  const today = useBenchDashboard().data?.asOf ?? null;

  if (allocations.isPending) return <Skeleton className="h-48" role="status" aria-label="Loading allocations" />;
  if (allocations.isError) return <BenchErrorState error={allocations.error} title="Couldn't load allocations" onRetry={() => void allocations.refetch()} />;
  const list = allocations.data.content;
  if (list.length === 0) return <EmptyState icon={CalendarRange} title="No allocations yet" description="This consultant has never been allocated to an engagement." className="py-10" />;

  return (
    <div className="space-y-8">
      <AllocationTimeline allocations={list} today={today} onOpen={onOpen} />
      <ul className="divide-y rounded-lg border" aria-label="Allocations">
        {[...list].reverse().map((a) => (
          <li key={a.id}>
            <button type="button" className="flex w-full flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 text-left hover:bg-muted/40" onClick={() => onOpen(a.id)}>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium">
                  {a.engagement.name} · {a.role}
                </span>
                <span className="block text-xs text-muted-foreground">
                  {a.engagement.client.name} · {dateRange(a.startDate, a.endDate)}
                </span>
              </span>
              <span className="text-sm font-semibold tabular-nums">{a.allocationPercent}%</span>
              <PeriodStatusBadge status={a.status} />
              <BillableBadge billable={a.billable} />
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
