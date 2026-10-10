import { AlarmClock, Bell, BellOff, CalendarClock, CheckCheck, CircleCheck, LifeBuoy, Megaphone, MessageSquareReply, RefreshCw, UserPlus, Workflow, type LucideIcon } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';

import { Button } from '@/components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Skeleton } from '@/components/ui/skeleton';
import { errorMessage } from '@/lib/api/errors';
import { formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';

import {
  notificationHref,
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useUnreadCount,
  type AppNotification,
  type NotificationType,
} from './api';

const TYPE_META: Record<NotificationType, { icon: LucideIcon; tone: string; label: string }> = {
  TASK_ASSIGNED: { icon: UserPlus, tone: 'text-primary', label: 'Assigned' },
  TASK_DUE_SOON: { icon: CalendarClock, tone: 'text-status-warning', label: 'Due soon' },
  TASK_OVERDUE: { icon: AlarmClock, tone: 'text-status-danger', label: 'Overdue' },
  TICKET_ASSIGNED: { icon: LifeBuoy, tone: 'text-primary', label: 'Ticket assigned' },
  TICKET_UPDATED: { icon: RefreshCw, tone: 'text-primary', label: 'Ticket update' },
  TICKET_REPLY: { icon: MessageSquareReply, tone: 'text-primary', label: 'New reply' },
  APPROVAL_REQUIRED: { icon: Workflow, tone: 'text-status-warning', label: 'Needs approval' },
  APPROVAL_DECIDED: { icon: CircleCheck, tone: 'text-status-success', label: 'Request decided' },
  ANNOUNCEMENT_PUBLISHED: { icon: Megaphone, tone: 'text-primary', label: 'Announcement' },
};

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const unread = useUnreadCount();
  const list = useNotifications(unreadOnly, open);
  const markRead = useMarkNotificationRead();
  const markAll = useMarkAllNotificationsRead();
  const count = unread.data?.unread ?? 0;

  const openNotification = (notification: AppNotification) => {
    if (!notification.read) markRead.mutate(notification.id);
    const href = notificationHref(notification);
    if (href) void navigate(href);
  };

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className="relative text-muted-foreground hover:text-foreground" aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}>
          <Bell />
          {count > 0 && (
            <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-status-danger px-1 text-[10px] ring-2 ring-card font-semibold text-white tabular-nums" aria-hidden>
              {count > 99 ? '99+' : count}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[22rem] p-0">
        <div className="flex items-center justify-between gap-2 border-b px-3 py-2.5">
          <p className="text-sm font-semibold">Notifications</p>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" aria-pressed={unreadOnly} onClick={() => setUnreadOnly((v) => !v)}>
              {unreadOnly ? 'Show all' : 'Unread only'}
            </Button>
            <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" disabled={count === 0 || markAll.isPending} onClick={() => markAll.mutate()}>
              <CheckCheck aria-hidden />
              Mark all read
            </Button>
          </div>
        </div>
        <div className="max-h-[26rem] overflow-y-auto py-1">
          {list.isPending ? (
            <div className="space-y-2 p-3" role="status" aria-label="Loading notifications">
              {Array.from({ length: 3 }, (_, i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </div>
          ) : list.isError ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">{errorMessage(list.error)}</p>
          ) : list.data.content.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
              <BellOff className="size-6 text-muted-foreground" aria-hidden />
              <p className="text-sm font-medium">{unreadOnly ? "You're all caught up" : 'No notifications yet'}</p>
              <p className="text-xs text-muted-foreground">Assignments, approvals, announcements and reminders show up here.</p>
            </div>
          ) : (
            list.data.content.map((notification) => {
              const meta = TYPE_META[notification.type];
              return (
                <DropdownMenuItem
                  key={notification.id}
                  onSelect={() => openNotification(notification)}
                  className={cn('items-start gap-3 rounded-none px-3 py-2.5', !notification.read && 'bg-accent/50')}
                >
                  <meta.icon className={cn('mt-0.5 size-4 shrink-0', meta.tone)} aria-label={meta.label} />
                  <div className="min-w-0 flex-1">
                    <p className={cn('truncate text-sm', !notification.read && 'font-semibold')}>{notification.title}</p>
                    {notification.body && <p className="line-clamp-2 text-xs text-muted-foreground">{notification.body}</p>}
                    <p className="mt-0.5 text-[11px] text-muted-foreground">
                      {meta.label} · {formatRelative(notification.createdAt)}
                    </p>
                  </div>
                  {!notification.read && (
                    <span className="mt-1.5 flex items-center">
                      <span className="size-2 rounded-full bg-primary" aria-hidden />
                      <span className="sr-only">Unread</span>
                    </span>
                  )}
                </DropdownMenuItem>
              );
            })
          )}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
