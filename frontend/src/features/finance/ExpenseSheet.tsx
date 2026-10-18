import { Download, FileText, FileUp, LoaderCircle, Pencil, Trash2 } from 'lucide-react';
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
import { statusLabel } from '@/features/search/api';
import { errorMessage } from '@/lib/api/errors';
import { formatBytes, formatLocalDate, formatRelative } from '@/lib/format';

import { downloadReceipt, useExpense, useExpenseReceipt, type Expense } from './api';
import { ExpenseFormDialog } from './ExpenseFormDialog';
import { FinanceErrorState } from './FinanceBadges';
import { BASE_CURRENCY, CATEGORY_LABELS, formatAud, formatMoney, formatRate } from './finance-meta';

/** Receipts as the server accepts them (UploadPolicy.ALLOWED): documents and images. */
const RECEIPT_TYPES = '.pdf,.png,.jpg,.jpeg,.gif,.webp,.doc,.docx,.xls,.xlsx,.csv,.txt';

/** One expense: amounts with GST, the AUD snapshot and the rate it used, the receipt and any linked approval; with its History. */
export function ExpenseSheet({ expenseId, onClose }: { expenseId: number | null; onClose: () => void }) {
  return (
    <Dialog open={expenseId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-xl">{expenseId !== null && <ExpenseBody expenseId={expenseId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function ExpenseBody({ expenseId, onClose }: { expenseId: number; onClose: () => void }) {
  const { canEdit } = useTool();
  const expense = useExpense(expenseId);
  const [editing, setEditing] = useState(false);

  if (expense.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the expense">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (expense.isError) return <FinanceErrorState error={expense.error} title="Couldn't load the expense" onRetry={() => void expense.refetch()} />;

  const e = expense.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <DialogTitle>{e.description}</DialogTitle>
        <DialogDescription>
          {formatLocalDate(e.expenseDate)} · {e.vendor?.name ?? (e.vendorId !== null ? 'Vendor in the recycle bin' : 'No vendor')}
        </DialogDescription>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="neutral">{CATEGORY_LABELS[e.category]}</Badge>
          {canEdit && (
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit expense
            </Button>
          )}
        </div>
      </DialogHeader>
      <DetailsWithHistory type="FINANCE_EXPENSE" id={e.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Amount (GST included)">
              <span className="font-semibold tabular-nums">{formatMoney(e.amount, e.currency)}</span>
            </Detail>
            <Detail label="GST">{e.gst === null ? <Muted>None recorded</Muted> : <span className="tabular-nums">{formatMoney(e.gst, e.currency)}</span>}</Detail>
            <Detail label="Amount without GST">{e.amountExGst === null ? <Muted>—</Muted> : <span className="tabular-nums">{formatMoney(e.amountExGst, e.currency)}</span>}</Detail>
            <Detail label="In AUD">
              <span className="font-semibold tabular-nums">{formatAud(e.amountAud)}</span>
              {e.gstAud !== null && <span className="block text-xs text-muted-foreground">GST {formatAud(e.gstAud)}</span>}
            </Detail>
            <Detail label="Exchange rate used">
              {e.currency === BASE_CURRENCY ? (
                <Muted>None needed (AUD)</Muted>
              ) : (
                <span>
                  1 {e.currency} = {formatRate(e.audRate)} AUD
                  {e.audRateDate && <span className="block text-xs text-muted-foreground">Rate from {formatLocalDate(e.audRateDate)}. Kept as it was when later rates change.</span>}
                </span>
              )}
            </Detail>
            <Detail label="Department">{e.department?.name ?? <Muted>None</Muted>}</Detail>
            <Detail label="Approval">
              {e.approval ? (
                <Link to={`/approvals?approval=${e.approval.id}`} className="hover:underline">
                  {e.approval.code} · {e.approval.title}
                  <span className="block text-xs text-muted-foreground">{statusLabel(e.approval.status)}</span>
                </Link>
              ) : (
                <Muted>Not linked</Muted>
              )}
            </Detail>
          </dl>
          {e.notes && <Detail label="Notes">{<span className="whitespace-pre-wrap">{e.notes}</span>}</Detail>}
          <ReceiptSection expense={e} canEdit={canEdit} />
        </div>
      </DetailsWithHistory>
      <ExpenseFormDialog open={editing} onOpenChange={setEditing} expense={e} onDeleted={onClose} />
    </div>
  );
}

/** The one receipt: anyone here downloads it (as an attachment); editors upload, replace and remove it. */
function ReceiptSection({ expense, canEdit }: { expense: Expense; canEdit: boolean }) {
  const input = useRef<HTMLInputElement>(null);
  const { upload, remove } = useExpenseReceipt(expense.id);
  const [confirmingRemove, setConfirmingRemove] = useState(false);
  const receipt = expense.receipt;

  async function run(action: Promise<unknown>, success: string) {
    try {
      await action;
      toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <section aria-label="Receipt" className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-medium">Receipt</h3>
        {canEdit && (
          <>
            <input
              ref={input}
              type="file"
              accept={RECEIPT_TYPES}
              className="sr-only"
              aria-label={receipt ? 'Replace the receipt' : 'Upload a receipt'}
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (file) void run(upload.mutateAsync(file), `${file.name} attached`);
              }}
            />
            <Button variant="outline" size="sm" disabled={upload.isPending} onClick={() => input.current?.click()}>
              {upload.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <FileUp aria-hidden />}
              {receipt ? 'Replace' : 'Upload'}
            </Button>
          </>
        )}
      </div>
      {!receipt ? (
        <p className="flex items-center gap-2 rounded-lg border border-dashed px-4 py-3 text-sm text-muted-foreground">
          <FileText className="size-4" aria-hidden />
          No receipt yet{canEdit ? '. Upload a PDF or photo of it.' : '.'}
        </p>
      ) : (
        <div className="flex items-center gap-3 rounded-lg border px-4 py-2.5">
          <FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium">{receipt.fileName}</p>
            <p className="text-xs text-muted-foreground">
              {formatBytes(receipt.sizeBytes)} · added {formatRelative(receipt.addedAt)}
            </p>
          </div>
          <Button variant="ghost" size="icon" className="size-8" aria-label={`Download ${receipt.fileName}`} onClick={() => void run(downloadReceipt(expense.id, receipt), 'Download started')}>
            <Download />
          </Button>
          {canEdit &&
            (confirmingRemove ? (
              <Button
                variant="ghost"
                size="sm"
                className="text-destructive"
                disabled={remove.isPending}
                onClick={() => void run(remove.mutateAsync(), 'Receipt removed').finally(() => setConfirmingRemove(false))}
              >
                Confirm
              </Button>
            ) : (
              <Button variant="ghost" size="icon" className="size-8 text-destructive" aria-label={`Remove ${receipt.fileName}`} onClick={() => setConfirmingRemove(true)}>
                <Trash2 />
              </Button>
            ))}
        </div>
      )}
    </section>
  );
}
