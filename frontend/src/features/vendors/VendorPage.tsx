import { ArrowLeft, CalendarClock, ExternalLink, FileSignature, Globe, Mail, Pencil, Phone, Plus, Receipt, Tag, UserRound, type LucideIcon } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Detail, Muted } from '@/features/celebrations/Details';
import { expenseHref, FINANCE_KEY, licenceHref } from '@/features/finance/finance-paths';
import { useCanViewHistory } from '@/features/history/api';
import { RecordHistory } from '@/features/history/RecordHistory';
import { useDepartmentTools } from '@/features/modules/api';
import { useTool } from '@/features/modules/tool-context';
import { statusLabel } from '@/features/search/api';
import { formatLocalDate } from '@/lib/format';
import { useIdParam } from '@/lib/use-id-param';

import { useContracts, useVendor, useVendorLinks, type Vendor } from './api';
import { ContractFormDialog } from './ContractFormDialog';
import { ContractSheet } from './ContractSheet';
import { ContractsTable } from './ContractsTable';
import { Rating, VendorErrorState, VendorStatusBadge } from './VendorBadges';
import { VendorFormDialog } from './VendorFormDialog';
import { CATEGORY_LABELS, safeWebsite } from './vendor-meta';
import { VENDOR_PATHS } from './vendor-paths';

/** One vendor: details and contacts, its contracts (with documents), linked licences and expenses, and History. */
export default function VendorPage() {
  const { canEdit } = useTool();
  const canViewHistory = useCanViewHistory();
  const navigate = useNavigate();
  const id = Number(useParams().id);
  const vendor = useVendor(Number.isInteger(id) && id > 0 ? id : null);
  const [openContract, setOpenContract] = useIdParam('contract');
  const [editing, setEditing] = useState(false);
  const [addingContract, setAddingContract] = useState(false);

  if (vendor.isPending) {
    return (
      <div className="space-y-6" role="status" aria-label="Loading the vendor">
        <Skeleton className="h-16 w-2/3" />
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
        <Skeleton className="h-80 rounded-xl" />
      </div>
    );
  }
  if (vendor.isError) {
    return (
      <Card>
        <VendorErrorState error={vendor.error} title="Couldn't open this vendor" onRetry={() => void vendor.refetch()} />
      </Card>
    );
  }

  const v = vendor.data;
  const website = safeWebsite(v.website);
  return (
    <div className="space-y-6">
      <Link to={VENDOR_PATHS.vendors} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden />
        Vendors
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h2 className="truncate text-xl font-semibold tracking-tight">{v.name}</h2>
          <div className="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <VendorStatusBadge status={v.status} />
            <span>{CATEGORY_LABELS[v.category]}</span>
            {v.country && <span>· {v.country}</span>}
            <Rating rating={v.rating} />
          </div>
        </div>
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            {v.status !== 'BLACKLISTED' && (
              <Button variant="outline" onClick={() => setAddingContract(true)}>
                <Plus aria-hidden />
                New contract
              </Button>
            )}
            <Button variant="outline" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit vendor
            </Button>
          </div>
        )}
      </div>

      <section aria-label="Vendor figures" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Contracts" icon={FileSignature}>
          <span className="text-lg font-semibold tabular-nums">{v.contractCount.toLocaleString()}</span>
        </Stat>
        <Stat label="Next renewal" icon={CalendarClock}>
          <span className="text-lg font-semibold">{v.nextRenewal ? formatLocalDate(v.nextRenewal) : '—'}</span>
        </Stat>
        <Stat label="Payment terms" icon={Tag}>
          <span className="text-lg font-semibold">{v.paymentTerms ?? '—'}</span>
        </Stat>
        <Stat label="Website" icon={Globe}>
          {website ? (
            <a href={website} target="_blank" rel="noopener noreferrer" className="inline-flex max-w-full items-center gap-1 truncate text-sm font-medium text-primary hover:underline">
              <span className="truncate">{new URL(website).host}</span>
              <ExternalLink className="size-3.5 shrink-0" aria-hidden />
            </a>
          ) : (
            <span className="text-lg font-semibold">—</span>
          )}
        </Stat>
      </section>

      <Card>
        <Tabs defaultValue={openContract !== null ? 'contracts' : 'overview'} className="pb-4">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="contracts">Contracts ({v.contractCount})</TabsTrigger>
            <TabsTrigger value="linked">Licences and expenses</TabsTrigger>
            {canViewHistory && <TabsTrigger value="history">History</TabsTrigger>}
          </TabsList>
          <div className="px-4 pt-5 sm:px-6">
            <TabsContent value="overview">
              <Overview vendor={v} />
            </TabsContent>
            <TabsContent value="contracts">
              <VendorContracts vendorId={v.id} onOpen={setOpenContract} />
            </TabsContent>
            <TabsContent value="linked">
              <LinkedRecords vendorId={v.id} />
            </TabsContent>
            {canViewHistory && (
              <TabsContent value="history" className="pb-2">
                <RecordHistory type="VENDOR" id={v.id} />
              </TabsContent>
            )}
          </div>
        </Tabs>
      </Card>

      <ContractSheet contractId={openContract} onClose={() => setOpenContract(null)} />
      {canEdit && (
        <>
          <VendorFormDialog open={editing} onOpenChange={setEditing} vendor={v} onDeleted={() => void navigate(VENDOR_PATHS.vendors)} />
          <ContractFormDialog open={addingContract} onOpenChange={setAddingContract} vendorId={v.id} onSaved={setOpenContract} />
        </>
      )}
    </div>
  );
}

function Stat({ label, icon: Icon, children }: { label: string; icon: LucideIcon; children: ReactNode }) {
  return (
    <div className="flex h-full min-w-0 flex-col rounded-xl border bg-card px-4 py-3.5 shadow-card">
      <p className="flex items-center justify-between gap-2 text-label font-medium text-muted-foreground">
        {label}
        <Icon className="size-4" aria-hidden />
      </p>
      <div className="mt-2 min-w-0">{children}</div>
    </div>
  );
}

function Overview({ vendor: v }: { vendor: Vendor }) {
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <section aria-labelledby="contacts-heading">
        <h3 id="contacts-heading" className="mb-3 flex items-center gap-2 text-sm font-medium">
          <UserRound className="size-4 text-muted-foreground" aria-hidden />
          Contacts
        </h3>
        {v.contacts.length === 0 ? (
          <p className="text-sm text-muted-foreground">No contacts recorded yet.</p>
        ) : (
          <ul className="divide-y rounded-lg border" aria-label="Contacts">
            {v.contacts.map((c, index) => (
              <li key={c.id} className="flex flex-wrap items-start gap-x-4 gap-y-1 px-4 py-3">
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">
                    {c.name}
                    {index === 0 && <span className="ml-2 text-xs font-normal text-muted-foreground">Main contact</span>}
                  </p>
                  {c.role && <p className="text-xs text-muted-foreground">{c.role}</p>}
                </div>
                <div className="flex flex-col gap-0.5 text-sm">
                  {c.email && (
                    <a href={`mailto:${c.email}`} className="inline-flex items-center gap-1.5 text-primary hover:underline">
                      <Mail className="size-3.5" aria-hidden />
                      {c.email}
                    </a>
                  )}
                  {c.phone && (
                    <a href={`tel:${c.phone.replace(/[^\d+]/g, '')}`} className="inline-flex items-center gap-1.5 hover:underline">
                      <Phone className="size-3.5 text-muted-foreground" aria-hidden />
                      {c.phone}
                    </a>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
      <dl className="grid content-start gap-x-6 gap-y-4 text-sm sm:grid-cols-2 lg:grid-cols-1">
        <Detail label="ABN / GST / tax ID">{v.taxId ?? <Muted>None</Muted>}</Detail>
        <Detail label="Address">{v.address ? <span className="whitespace-pre-wrap">{v.address}</span> : <Muted>None</Muted>}</Detail>
        <Detail label="Notes">{v.notes ? <span className="whitespace-pre-wrap">{v.notes}</span> : <Muted>None</Muted>}</Detail>
      </dl>
    </div>
  );
}

function VendorContracts({ vendorId, onOpen }: { vendorId: number; onOpen: (id: number) => void }) {
  const contracts = useContracts({ vendorId, size: 100, sort: 'start,desc' });
  if (contracts.isPending) return <Skeleton className="h-40" role="status" aria-label="Loading contracts" />;
  if (contracts.isError) return <VendorErrorState error={contracts.error} title="Couldn't load contracts" onRetry={() => void contracts.refetch()} />;
  if (contracts.data.content.length === 0) return <EmptyState icon={FileSignature} title="No contracts yet" className="py-10" />;
  return (
    <div className="-mx-4 sm:-mx-6">
      <ContractsTable contracts={contracts.data.content} onOpen={onOpen} showVendor={false} />
    </div>
  );
}

/** Where a linked record opens, by its group's kind (VendorLinkSource.kind). */
const LINK_HREFS: Record<string, (id: number) => string> = { LICENCES: licenceHref, EXPENSES: expenseHref };

/**
 * Licences and expenses (Finance Tracker) that name this vendor. The server lists only the kinds the viewer may see
 * (FINANCE_VIEW); each opens on its Finance Tracker page when the viewer may open that tool.
 */
function LinkedRecords({ vendorId }: { vendorId: number }) {
  const links = useVendorLinks(vendorId);
  const tools = useDepartmentTools();
  const financeOpen = tools.data?.some((t) => t.key === FINANCE_KEY) ?? false;
  if (links.isPending) return <Skeleton className="h-32" role="status" aria-label="Loading linked records" />;
  if (links.isError) return <VendorErrorState error={links.error} title="Couldn't load linked records" onRetry={() => void links.refetch()} />;
  if (links.data.length === 0) {
    return (
      <EmptyState
        icon={Receipt}
        title="No licences or expenses to show"
        description="Tools, licences and expenses from the Finance Tracker that name this vendor appear here. Finance data shows only to people who can see finance."
        className="py-10"
      />
    );
  }
  return (
    <div className="space-y-6">
      {links.data.map((group) => (
        <section key={group.kind} aria-label={group.label}>
          <h3 className="mb-2 text-sm font-medium">
            {group.label} <span className="font-normal text-muted-foreground">({group.total.toLocaleString()})</span>
          </h3>
          {group.items.length === 0 ? (
            <p className="text-sm text-muted-foreground">None.</p>
          ) : (
            <ul className="divide-y rounded-lg border">
              {group.items.map((item) => (
                <li key={item.id} className="flex items-center gap-3 px-4 py-2.5">
                  <div className="min-w-0 flex-1">
                    {financeOpen && LINK_HREFS[group.kind] ? (
                      <Link to={LINK_HREFS[group.kind]!(item.id)} className="block truncate text-sm font-medium hover:underline">
                        {item.title}
                      </Link>
                    ) : (
                      <p className="truncate text-sm font-medium">{item.title}</p>
                    )}
                    {item.detail && <p className="truncate text-xs text-muted-foreground">{item.detail}</p>}
                  </div>
                  {item.status && <span className="text-xs text-muted-foreground">{statusLabel(item.status)}</span>}
                </li>
              ))}
            </ul>
          )}
          {group.total > group.items.length && <p className="mt-2 text-xs text-muted-foreground">Showing the newest {group.items.length} of {group.total.toLocaleString()}.</p>}
        </section>
      ))}
    </div>
  );
}
