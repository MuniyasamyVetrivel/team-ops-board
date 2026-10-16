import { CircleAlert, LoaderCircle, RotateCcw, Trash2, TriangleAlert } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Dialog, DialogBody, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/api/errors';

import { usePurgeItem, useRestoreCheck, useRestoreItem, type RecycleBinItem } from './api';
import { confirmationMatches, itemName } from './bin-rules';

function Notice({ tone, children }: { tone: 'danger' | 'warning'; children: ReactNode }) {
  const Icon = tone === 'danger' ? CircleAlert : TriangleAlert;
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={
        tone === 'danger'
          ? 'flex gap-2 rounded-lg border border-status-danger/30 bg-status-danger/10 p-3 text-sm text-status-danger'
          : 'flex gap-2 rounded-lg border border-status-warning/30 bg-status-warning/10 p-3 text-sm text-status-warning'
      }
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}

/**
 * Restore: checks first whether the item can come back as it was. A clash that blocks it (another employee now has its
 * employee ID, another page its URL) disables the restore; warnings (its work email now belongs to someone else) are
 * shown but don't stop it.
 */
export function RestoreDialog({ item, onClose }: { item: RecycleBinItem | null; onClose: () => void }) {
  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>{item && <RestoreBody key={item.id} item={item} onClose={onClose} />}</DialogContent>
    </Dialog>
  );
}

function RestoreBody({ item, onClose }: { item: RecycleBinItem; onClose: () => void }) {
  const check = useRestoreCheck(item.id);
  const restore = useRestoreItem();
  const [error, setError] = useState<string | null>(null);
  const name = itemName(item);

  const submit = async () => {
    setError(null);
    try {
      await restore.mutateAsync(item.id);
      toast.success(`${name} restored`);
      onClose();
    } catch (e) {
      setError(errorMessage(e, "Couldn't restore this item"));
    }
  };

  const blocked = check.data ? !check.data.canRestore : false;
  return (
    <>
      <DialogHeader>
        <DialogTitle>Restore {item.entityTypeLabel.toLowerCase()}?</DialogTitle>
        <DialogDescription>
          <span className="font-medium text-foreground">{name}</span> comes back exactly as it was, with its comments, attachments and other details.
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-3">
        {check.isPending ? (
          <Skeleton className="h-12 rounded-lg" role="status" aria-label="Checking for clashes" />
        ) : check.isError ? (
          <Notice tone="warning">Couldn't check for clashes ({errorMessage(check.error)}). The restore will still stop if something clashes.</Notice>
        ) : (
          <>
            {check.data.blockMessage && <Notice tone="danger">{check.data.blockMessage}</Notice>}
            {check.data.warnings.map((warning) => (
              <Notice key={warning} tone="warning">
                {warning}
              </Notice>
            ))}
            {check.data.canRestore && check.data.warnings.length === 0 && <p className="text-sm text-muted-foreground">No clashes with live records.</p>}
          </>
        )}
        {error && <Notice tone="danger">{error}</Notice>}
      </DialogBody>
      <DialogFooter>
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
        <Button onClick={() => void submit()} disabled={check.isPending || blocked || restore.isPending}>
          {restore.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <RotateCcw aria-hidden />}
          {check.data && check.data.warnings.length > 0 ? 'Restore anyway' : 'Restore'}
        </Button>
      </DialogFooter>
    </>
  );
}

/** Permanent delete: the person types the item's name, so the wrong one is never removed by a slip. */
export function PurgeDialog({ item, onClose }: { item: RecycleBinItem | null; onClose: () => void }) {
  return (
    <Dialog open={item !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>{item && <PurgeBody key={item.id} item={item} onClose={onClose} />}</DialogContent>
    </Dialog>
  );
}

function PurgeBody({ item, onClose }: { item: RecycleBinItem; onClose: () => void }) {
  const purge = usePurgeItem();
  const [typed, setTyped] = useState('');
  const [error, setError] = useState<string | null>(null);
  const name = itemName(item);
  const matches = confirmationMatches(typed, item.confirmationText);

  const submit = async () => {
    if (!matches) return;
    setError(null);
    try {
      await purge.mutateAsync({ id: item.id, confirm: typed });
      toast.success(`${name} deleted permanently`);
      onClose();
    } catch (e) {
      setError(errorMessage(e, "Couldn't delete this item permanently"));
    }
  };

  return (
    <form
      className="contents"
      onSubmit={(e) => {
        e.preventDefault();
        void submit();
      }}
    >
      <DialogHeader>
        <DialogTitle>Delete “{name}” permanently?</DialogTitle>
        <DialogDescription>
          This {item.entityTypeLabel.toLowerCase()} and everything attached to it (comments, files, history of its children) are removed for good. This can't be undone.
        </DialogDescription>
      </DialogHeader>
      <DialogBody className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="purge-confirm">
            Type <span className="font-mono font-semibold">{item.confirmationText}</span> to confirm
          </Label>
          <Input id="purge-confirm" autoComplete="off" autoFocus value={typed} onChange={(e) => setTyped(e.target.value)} aria-invalid={typed !== '' && !matches ? true : undefined} />
        </div>
        {error && <Notice tone="danger">{error}</Notice>}
      </DialogBody>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" variant="destructive" disabled={!matches || purge.isPending}>
          {purge.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <Trash2 aria-hidden />}
          Delete permanently
        </Button>
      </DialogFooter>
    </form>
  );
}
