import { Download, FileText, FileUp, LoaderCircle, Pencil, RefreshCw, Trash2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Detail, Muted } from '@/features/celebrations/Details';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';
import { useTool } from '@/features/modules/tool-context';
import { errorMessage } from '@/lib/api/errors';
import { formatBytes, formatLocalDate, formatRelative } from '@/lib/format';

import { downloadContractDocument, useContract, useContractDocuments, type Contract } from './api';
import { ContractFormDialog } from './ContractFormDialog';
import { ContractStatusBadge, ContractValue, RenewalBadge, VendorErrorState } from './VendorBadges';
import { noticeLabel } from './vendor-meta';
import { vendorPath } from './vendor-paths';

/** Mirrors ContractDocumentService.MAX_DOCUMENTS. */
const MAX_DOCUMENTS = 20;

/** One contract: dates, value (hidden without FINANCE_VIEW), renewal terms and documents; with its History. */
export function ContractSheet({ contractId, onClose }: { contractId: number | null; onClose: () => void }) {
  return (
    <Dialog open={contractId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-xl">{contractId !== null && <ContractBody contractId={contractId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function ContractBody({ contractId, onClose }: { contractId: number; onClose: () => void }) {
  const { canEdit } = useTool();
  const contract = useContract(contractId);
  const [editing, setEditing] = useState(false);

  if (contract.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the contract">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (contract.isError) return <VendorErrorState error={contract.error} title="Couldn't load the contract" onRetry={() => void contract.refetch()} />;

  const c = contract.data;
  const notice = noticeLabel(c);
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{c.title}</DialogTitle>
        <DialogDescription>
          <Link to={vendorPath(c.vendor.id)} className="hover:underline">
            {c.vendor.name}
          </Link>
        </DialogDescription>
        <div className="flex flex-wrap items-center gap-2">
          <ContractStatusBadge status={c.status} />
          <RenewalBadge contract={c} />
          {c.autoRenew && (
            <Badge tone="neutral">
              <RefreshCw aria-hidden />
              Auto-renews
            </Badge>
          )}
          {canEdit && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit contract
            </Button>
          )}
        </div>
      </DialogHeader>
      <DetailsWithHistory type="VENDOR_CONTRACT" id={c.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Value">
              <ContractValue contract={c} className="font-semibold" />
            </Detail>
            <Detail label="Dates">
              {formatLocalDate(c.startDate)} – {c.endDate ? formatLocalDate(c.endDate) : 'no end date'}
            </Detail>
            <Detail label={c.renewalDate ? 'Renewal date' : 'Renews or ends on'}>{c.renewsOn ? formatLocalDate(c.renewsOn) : <Muted>No renewal or end date</Muted>}</Detail>
            <Detail label="Notice period">{c.noticePeriodDays === null ? <Muted>None recorded</Muted> : `${c.noticePeriodDays} days`}</Detail>
            <Detail label="Give notice by">
              {c.noticeBy ? (
                <>
                  {formatLocalDate(c.noticeBy)}
                  {notice && <span className="block text-xs text-muted-foreground">{notice}</span>}
                </>
              ) : (
                <Muted>—</Muted>
              )}
            </Detail>
            <Detail label="Currency">{c.currency}</Detail>
          </dl>
          {c.notes && <Detail label="Notes">{<span className="whitespace-pre-wrap">{c.notes}</span>}</Detail>}
          <Documents contract={c} canEdit={canEdit} />
        </div>
      </DetailsWithHistory>
      <ContractFormDialog open={editing} onOpenChange={setEditing} contract={c} onDeleted={onClose} />
    </div>
  );
}

/** Documents on the contract: anyone here downloads (as an attachment); editors upload and remove. */
function Documents({ contract, canEdit }: { contract: Contract; canEdit: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const { upload, remove } = useContractDocuments(contract.id);
  const [removing, setRemoving] = useState<number | null>(null);

  async function run(action: Promise<unknown>, success: string) {
    try {
      await action;
      toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section aria-label="Documents" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">Documents</h3>
        {canEdit && (
          <>
            <input
              ref={input}
              type="file"
              className="sr-only"
              aria-label="Upload a document"
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (file) void run(upload.mutateAsync(file), `${file.name} uploaded`);
              }}
            />
            <Button variant="outline" size="sm" disabled={upload.isPending || contract.documents.length >= MAX_DOCUMENTS} onClick={() => input.current?.click()}>
              {upload.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <FileUp aria-hidden />}
              Upload
            </Button>
          </>
        )}
      </div>
      {contract.documents.length === 0 ? (
        <p className="flex items-center gap-2 rounded-lg border border-dashed px-4 py-3 text-sm text-muted-foreground">
          <FileText className="size-4" aria-hidden />
          No documents yet{canEdit ? '. Upload the signed contract, order forms or quotes.' : '.'}
        </p>
      ) : (
        <ul className="divide-y rounded-lg border">
          {contract.documents.map((d) => (
            <li key={d.fileId} className="flex items-center gap-3 px-4 py-2.5">
              <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium">{d.fileName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatBytes(d.sizeBytes)} · {d.addedBy ? `${d.addedBy.fullName}, ` : ''}
                  {formatRelative(d.addedAt)}
                </p>
              </div>
              <Button variant="ghost" size="icon" className="size-8" aria-label={`Download ${d.fileName}`} onClick={() => void run(downloadContractDocument(contract.id, d), 'Download started')}>
                <Download />
              </Button>
              {canEdit &&
                (removing === d.fileId ? (
                  <Button variant="ghost" size="sm" className="text-destructive" disabled={remove.isPending} onClick={() => void run(remove.mutateAsync(d.fileId), `${d.fileName} removed`).finally(() => setRemoving(null))}>
                    Confirm
                  </Button>
                ) : (
                  <Button variant="ghost" size="icon" className="size-8 text-destructive" aria-label={`Remove ${d.fileName}`} onClick={() => setRemoving(d.fileId)}>
                    <Trash2 />
                  </Button>
                ))}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
