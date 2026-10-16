import { Camera, ChevronDown, CircleUser, KeyRound, LogOut } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { UserAvatar } from '@/components/common/UserAvatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { hasPermission, primaryRoleLabel } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { PhotoDialog } from '@/features/profile-photo/PhotoDialog';

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [photoOpen, setPhotoOpen] = useState(false);
  if (!user) return null;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger className="flex items-center gap-2.5 rounded-lg py-1 pr-2 pl-1 text-left outline-none hover:bg-muted focus-visible:ring-[3px] focus-visible:ring-ring/40">
          <UserAvatar name={user.fullName} user={user} />
          <div className="hidden leading-tight sm:block">
            <p className="text-sm font-semibold">{user.fullName}</p>
            <p className="text-xs text-muted-foreground">{primaryRoleLabel(user)}</p>
          </div>
          <ChevronDown className="size-4 text-muted-foreground" aria-hidden />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64">
          <DropdownMenuLabel className="font-normal">
            <p className="text-sm font-medium">{user.fullName}</p>
            <p className="truncate text-xs text-muted-foreground">{user.email}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              {primaryRoleLabel(user)} · {user.department.name}
              {user.employeeId && ` · ${user.employeeId}`}
            </p>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {hasPermission(user, 'TEAM_VIEW') && (
            <DropdownMenuItem onSelect={() => navigate(`/team/${user.id}`)}>
              <CircleUser aria-hidden />
              My profile
            </DropdownMenuItem>
          )}
          <DropdownMenuItem onSelect={() => setPhotoOpen(true)}>
            <Camera aria-hidden />
            {user.photoVersion != null ? 'Change photo' : 'Add a photo'}
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => navigate('/change-password')}>
            <KeyRound aria-hidden />
            Change password
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => void logout()}>
            <LogOut aria-hidden />
            Sign out
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <PhotoDialog open={photoOpen} onOpenChange={setPhotoOpen} userId={null} name={user.fullName} current={user} />
    </>
  );
}
