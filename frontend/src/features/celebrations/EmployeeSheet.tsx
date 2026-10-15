import { Award, Cake, ImageUp, LoaderCircle, Pencil, Trash2 } from 'lucide-react';
import { useRef, useState } from 'react';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { UserCell } from '@/components/common/UserAvatar';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/api/errors';
import { DetailsWithHistory } from '@/features/history/DetailsWithHistory';

import { useEmployee, useEmployeePhotoMutations, type Employee } from './api';
import { EmployeeAvatar } from './CelebrationBadges';
import { dayMonth, EMPLOYEE_STATUS_LABELS, formatShortDate, yearsLabel } from './celebration-meta';
import { Detail, Muted } from './Details';
import { EmployeeFormDialog } from './EmployeeFormDialog';

/** An employee: their celebrations (birthday day and month; the year for editors), details and photo. */
export function EmployeeSheet({ employeeId, onClose }: { employeeId: number | null; onClose: () => void }) {
  return (
    <Dialog open={employeeId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="sm:max-w-lg">{employeeId !== null && <EmployeeBody employeeId={employeeId} onClose={onClose} />}</SheetContent>
    </Dialog>
  );
}

function EmployeeBody({ employeeId, onClose }: { employeeId: number; onClose: () => void }) {
  const employee = useEmployee(employeeId);
  const [editing, setEditing] = useState(false);

  if (employee.isPending) {
    return (
      <div className="space-y-4 p-6" role="status" aria-label="Loading the employee">
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (employee.isError) return <ErrorState error={employee.error} title="Couldn't load the employee" onRetry={() => void employee.refetch()} />;

  const e = employee.data;
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <DialogHeader className="pr-12">
        <div className="flex items-center gap-4">
          <EmployeeAvatar employee={e} size="lg" />
          <div className="min-w-0">
            <DialogTitle>{e.fullName}</DialogTitle>
            <DialogDescription>{e.designation}</DialogDescription>
            {e.status === 'LEFT' && (
              <Badge tone="neutral" className="mt-1">
                {EMPLOYEE_STATUS_LABELS.LEFT}
              </Badge>
            )}
          </div>
        </div>
        {e.canEdit && (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
              <Pencil aria-hidden />
              Edit employee
            </Button>
            <PhotoButtons employee={e} />
          </div>
        )}
      </DialogHeader>
      <DetailsWithHistory type="EMPLOYEE" id={e.id}>
        <div className="min-h-0 flex-1 space-y-6 overflow-y-auto px-6 pb-6">
          <section aria-label="Celebrations" className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-xl border p-4">
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <Cake className="size-4 text-occasion-birthday" aria-hidden />
                Birthday
              </p>
              <p className="mt-1 font-semibold">{dayMonth(e.birthMonth, e.birthDay)}</p>
              <p className="text-xs text-muted-foreground">{e.dateOfBirth ? `Born ${formatShortDate(e.dateOfBirth)}` : `Next on ${formatShortDate(e.nextBirthday)}`}</p>
            </div>
            <div className="rounded-xl border p-4">
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <Award className="size-4 text-occasion-anniversary" aria-hidden />
                Work anniversary
              </p>
              {e.nextAnniversary && e.nextAnniversaryYears !== null ? (
                <>
                  <p className="mt-1 font-semibold">{yearsLabel(e.nextAnniversaryYears)}</p>
                  <p className="text-xs text-muted-foreground">On {formatShortDate(e.nextAnniversary)}</p>
                </>
              ) : (
                <p className="mt-1 text-sm text-muted-foreground">No date of joining</p>
              )}
            </div>
          </section>
          <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Detail label="Employee ID">{e.employeeCode}</Detail>
            <Detail label="Reports to">{e.reportingManager}</Detail>
            <Detail label="Department">{e.department?.name ?? <Muted>None</Muted>}</Detail>
            <Detail label="Joined">{formatShortDate(e.dateOfJoining) ?? <Muted>Not recorded</Muted>}</Detail>
            <Detail label="Work email">{e.workEmail ?? <Muted>None</Muted>}</Detail>
            <Detail label="Office">{e.officeLocation ?? <Muted>None</Muted>}</Detail>
            <Detail label="App user">{e.user ? <UserCell name={e.user.fullName} detail={e.user.email} /> : <Muted>Does not sign in</Muted>}</Detail>
          </dl>
        </div>
      </DetailsWithHistory>
      <EmployeeFormDialog open={editing} onOpenChange={setEditing} employee={e} onDeleted={onClose} />
    </div>
  );
}

/** Upload or remove the photo (PNG, JPG, GIF or WebP; the server checks type and size). */
function PhotoButtons({ employee }: { employee: Employee }) {
  const input = useRef<HTMLInputElement>(null);
  const { upload, remove } = useEmployeePhotoMutations(employee.id);

  async function run(action: Promise<unknown>, success: string) {
    try {
      await action;
      toast.success(success);
    } catch (error) {
      toast.error(errorMessage(error));
    }
  }

  return (
    <>
      <input
        ref={input}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        className="sr-only"
        aria-label="Upload a photo"
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) void run(upload.mutateAsync(file), 'Photo updated');
        }}
      />
      <Button variant="outline" size="sm" disabled={upload.isPending} onClick={() => input.current?.click()}>
        {upload.isPending ? <LoaderCircle className="animate-spin" aria-hidden /> : <ImageUp aria-hidden />}
        {employee.hasPhoto ? 'Change photo' : 'Add photo'}
      </Button>
      {employee.hasPhoto && (
        <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={() => void run(remove.mutateAsync(), 'Photo removed')}>
          <Trash2 aria-hidden />
          Remove photo
        </Button>
      )}
    </>
  );
}
