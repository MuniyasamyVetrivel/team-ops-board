import { CalendarClock, RefreshCw, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';

import { EmptyState } from '@/components/common/EmptyState';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { ToolSection } from '@/features/modules/ToolSection';
import { formatLocalDate } from '@/lib/format';
import { useIdParam } from '@/lib/use-id-param';

import { useContracts, type Contract } from './api';
import { ContractSheet } from './ContractSheet';
import { ContractValue, RenewalBadge, VendorErrorState } from './VendorBadges';
import { daysUntilNotice, noticeLabel, renewalGroups } from './vendor-meta';
import { vendorPath } from './vendor-paths';

const WINDOWS = [30, 60, 90, 180];

/** The server returns at most this many; more would be a page the list does not show (it says so). */
const LIMIT = 100;

/**
 * Contracts that renew or end soon (the renewal date, or else the end date), soonest first, with the notice deadline.
 * Days come from the server's business date, never the browser clock.
 */
export default function RenewalsPage() {
  const [withinDays, setWithinDays] = useState(30);
  const [openId, setOpenId] = useIdParam('contract');
  const renewals = useContracts({ renewalWithinDays: withinDays, size: LIMIT, sort: 'renewal,asc' });

  return (
    <div className="space-y-4">
      <ToolSection
        title="Upcoming renewals"
        description="Contracts that renew or end soon. Reminders go out 30 and 7 days before."
        actions={
          <Select aria-label="Renewing within" value={withinDays} onChange={(e) => setWithinDays(Number(e.target.value))} className="w-auto">
            {WINDOWS.map((days) => (
              <option key={days} value={days}>
                Next {days} days
              </option>
            ))}
          </Select>
        }
      />
      {renewals.isPending ? (
        <Card className="space-y-3 p-4" role="status" aria-label="Loading renewals">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-14" />
          ))}
        </Card>
      ) : renewals.isError ? (
        <Card>
          <VendorErrorState error={renewals.error} title="Couldn't load renewals" onRetry={() => void renewals.refetch()} />
        </Card>
      ) : renewals.data.content.length === 0 ? (
        <Card>
          <EmptyState icon={CalendarClock} title={`Nothing renews or ends in the next ${withinDays} days`} description="Choose a longer window to look further ahead." />
        </Card>
      ) : (
        <>
          {renewalGroups(renewals.data.content).map((group) => (
            <Card key={group.key}>
              <h3 className="border-b px-6 py-3 text-sm font-semibold">
                {group.title} <span className="font-normal text-muted-foreground">({group.contracts.length})</span>
              </h3>
              <ul className="divide-y" aria-label={group.title}>
                {group.contracts.map((c) => (
                  <RenewalRow key={c.id} contract={c} onOpen={() => setOpenId(c.id)} />
                ))}
              </ul>
            </Card>
          ))}
          {renewals.data.totalElements > renewals.data.content.length && (
            <p className="text-xs text-muted-foreground">
              Showing {renewals.data.content.length} of {renewals.data.totalElements}. Use a shorter window, or the Contracts tab, to see the rest.
            </p>
          )}
        </>
      )}
      <ContractSheet contractId={openId} onClose={() => setOpenId(null)} />
    </div>
  );
}

function RenewalRow({ contract: c, onOpen }: { contract: Contract; onOpen: () => void }) {
  const notice = noticeLabel(c);
  const noticePassed = (daysUntilNotice(c) ?? 0) < 0;
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 px-6 py-3">
      <div className="min-w-0 flex-1">
        <button type="button" className="block max-w-full truncate text-left text-sm font-medium hover:underline" onClick={onOpen}>
          {c.title}
        </button>
        <Link to={vendorPath(c.vendor.id)} className="block truncate text-xs text-muted-foreground hover:underline">
          {c.vendor.name}
        </Link>
      </div>
      <ContractValue contract={c} className="text-sm" />
      <div className="flex flex-wrap items-center gap-2">
        {c.autoRenew && (
          <Badge tone="neutral">
            <RefreshCw aria-hidden />
            Auto-renews
          </Badge>
        )}
        <RenewalBadge contract={c} />
      </div>
      <div className="w-44 text-right text-xs">
        <span className="block text-sm tabular-nums">{formatLocalDate(c.renewsOn)}</span>
        {notice && (
          <span className={noticePassed ? 'inline-flex items-center gap-1 font-medium text-status-danger' : 'text-muted-foreground'}>
            {noticePassed && <TriangleAlert className="size-3" aria-hidden />}
            {notice}
            {c.noticeBy && !noticePassed ? ` (${formatLocalDate(c.noticeBy)})` : ''}
          </span>
        )}
      </div>
    </li>
  );
}
