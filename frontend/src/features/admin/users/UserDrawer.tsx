import { zodResolver } from '@hookform/resolvers/zod';
import { LoaderCircle } from 'lucide-react';
import { useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { toast } from 'sonner';

import { ErrorState } from '@/components/common/ErrorState';
import { FormBanner } from '@/components/common/FormField';
import { StatusBadge } from '@/components/common/StatusBadge';
import { UserAvatar } from '@/components/common/UserAvatar';
import { Button } from '@/components/ui/button';
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog';
import { Skeleton } from '@/components/ui/skeleton';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { applyServerErrors } from '@/lib/api/form-errors';

import { AccessEditor } from './AccessEditor';
import { useUpdateUser, useUser, type UserDetail } from './api';
import { ProfileFields } from './ProfileFields';
import { RoleBadges } from './RoleBadges';
import { profileSchema, toProfilePayload, type ProfileFormInput, type ProfileFormValues } from './schemas';
import { SecurityPanel } from './SecurityPanel';

const SERVER_FIELDS = ['email', 'firstName', 'lastName', 'jobTitle', 'phone', 'location', 'workingHours', 'departmentId', 'reportsToId', 'weeklyCapacityHours'] as const;

interface UserDrawerProps {
  userId: number | null;
  onClose: () => void;
}

export function UserDrawer({ userId, onClose }: UserDrawerProps) {
  const query = useUser(userId);

  return (
    <Dialog open={userId !== null} onOpenChange={(open) => !open && onClose()}>
      <SheetContent aria-describedby={undefined} className="sm:max-w-2xl">
        {query.isPending ? (
          <div className="space-y-4 p-6">
            <DialogTitle className="sr-only">Loading user</DialogTitle>
            <Skeleton className="h-14 w-64" />
            <Skeleton className="h-80" />
          </div>
        ) : query.isError ? (
          <div className="p-6">
            <DialogTitle className="sr-only">Error</DialogTitle>
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          </div>
        ) : (
          // Remount per user so forms start from that user's values.
          <UserDrawerBody key={query.data.id} user={query.data} />
        )}
      </SheetContent>
    </Dialog>
  );
}

function UserDrawerBody({ user }: { user: UserDetail }) {
  return (
    <>
      <div className="flex items-start gap-4 border-b px-6 py-5 pr-12">
        <UserAvatar name={user.fullName} size="lg" />
        <div className="min-w-0 space-y-1.5">
          <DialogTitle className="truncate text-lg">{user.fullName}</DialogTitle>
          <DialogDescription className="truncate">
            {user.jobTitle ?? 'No job title'} · {user.department.name}
          </DialogDescription>
          <div className="flex flex-wrap items-center gap-1.5">
            <StatusBadge status={user.status} />
            <RoleBadges roles={user.roles} />
          </div>
        </div>
      </div>
      <Tabs defaultValue="profile" className="flex min-h-0 flex-1 flex-col">
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="access">Access</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
        </TabsList>
        <div className="flex-1 overflow-y-auto px-6 pt-5">
          <TabsContent value="profile" className="pb-5">
            <ProfileForm user={user} />
          </TabsContent>
          <TabsContent value="access">
            <AccessEditor user={user} />
          </TabsContent>
          <TabsContent value="security" className="pb-5">
            <SecurityPanel user={user} />
          </TabsContent>
        </div>
      </Tabs>
    </>
  );
}

function ProfileForm({ user }: { user: UserDetail }) {
  const updateUser = useUpdateUser(user.id);
  const [banner, setBanner] = useState<string | null>(null);
  const form = useForm<ProfileFormInput, unknown, ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      jobTitle: user.jobTitle ?? '',
      phone: user.phone ?? '',
      location: user.location ?? '',
      workingHours: user.workingHours ?? '',
      departmentId: String(user.department.id),
      reportsToId: user.reportsTo ? String(user.reportsTo.id) : '',
      weeklyCapacityHours: user.weeklyCapacityHours,
    },
  });
  const { handleSubmit, setError, reset, formState: { isSubmitting, isDirty } } = form;

  const onSubmit = handleSubmit(async (values) => {
    setBanner(null);
    try {
      await updateUser.mutateAsync(toProfilePayload(values));
      reset(values);
      toast.success('Profile saved');
    } catch (error) {
      setBanner(applyServerErrors(error, setError, SERVER_FIELDS));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <FormBanner message={banner} />
      <FormProvider {...form}>
        <ProfileFields userId={user.id} />
      </FormProvider>
      <div className="flex items-center justify-between gap-2 border-t pt-4">
        <Button asChild variant="link" className="px-0">
          <Link to={`/team/${user.id}`}>View team profile</Link>
        </Button>
        <Button type="submit" disabled={!isDirty || isSubmitting}>
          {isSubmitting && <LoaderCircle className="animate-spin" aria-hidden />}
          Save profile
        </Button>
      </div>
    </form>
  );
}
