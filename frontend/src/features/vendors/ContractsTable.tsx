import { FileText } from 'lucide-react';
import { Link } from 'react-router';

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatLocalDate } from '@/lib/format';
import { cn } from '@/lib/utils';

import type { Contract } from './api';
import { ContractStatusBadge, ContractValue, RenewalBadge } from './VendorBadges';
import { noticeLabel } from './vendor-meta';
import { vendorPath } from './vendor-paths';

/** Contracts as rows; the title opens the contract drawer. The vendor column is left out on a vendor's own page. */
export function ContractsTable({ contracts, onOpen, showVendor = true, dimmed }: { contracts: Contract[]; onOpen: (id: number) => void; showVendor?: boolean; dimmed?: boolean }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Contract</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Value</TableHead>
          <TableHead>Dates</TableHead>
          <TableHead>Renews or ends</TableHead>
          <TableHead>Give notice by</TableHead>
          <TableHead className="text-right">Documents</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody className={cn(dimmed && 'opacity-60')}>
        {contracts.map((c) => {
          const notice = noticeLabel(c);
          return (
            <TableRow key={c.id}>
              <TableCell className="max-w-xs">
                <button type="button" className="block max-w-full text-left" onClick={() => onOpen(c.id)}>
                  <span className="block truncate font-medium hover:underline">{c.title}</span>
                </button>
                {showVendor && (
                  <Link to={vendorPath(c.vendor.id)} className="block truncate text-xs text-muted-foreground hover:underline">
                    {c.vendor.name}
                  </Link>
                )}
              </TableCell>
              <TableCell>
                <ContractStatusBadge status={c.status} />
              </TableCell>
              <TableCell className="text-right text-sm whitespace-nowrap">
                <ContractValue contract={c} />
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap">
                {formatLocalDate(c.startDate)} – {c.endDate ? formatLocalDate(c.endDate) : 'open'}
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap">
                {c.renewsOn ? formatLocalDate(c.renewsOn) : <span className="text-muted-foreground">—</span>}
                <span className="mt-1 flex">
                  <RenewalBadge contract={c} />
                </span>
              </TableCell>
              <TableCell className="text-sm whitespace-nowrap">
                {c.noticeBy ? formatLocalDate(c.noticeBy) : <span className="text-muted-foreground">—</span>}
                {notice && <span className={cn('block text-xs', notice === 'Notice deadline passed' ? 'text-status-danger' : 'text-muted-foreground')}>{notice}</span>}
              </TableCell>
              <TableCell className="text-right text-sm tabular-nums">
                <span className="inline-flex items-center gap-1" aria-label={`${c.documents.length} documents`}>
                  <FileText className="size-3.5 text-muted-foreground" aria-hidden />
                  {c.documents.length}
                </span>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
