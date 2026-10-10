import {
  BookOpen,
  Building,
  CalendarDays,
  ChartColumn,
  ChartLine,
  ClipboardList,
  FileText,
  FolderKanban,
  Gauge,
  LayoutDashboard,
  LifeBuoy,
  Link2,
  ListTodo,
  Mail,
  Megaphone,
  MousePointerClick,
  Repeat,
  ScrollText,
  Search,
  Settings,
  SquarePen,
  Target,
  Ticket,
  Timer,
  UserPlus,
  Users,
  UsersRound,
  Workflow,
  type LucideIcon,
} from 'lucide-react';

import { hasPermission, type PermissionCode, type RoleCode } from '@/features/auth/permissions';

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  /** Required to see the item and open the route. The backend enforces the same rule on its APIs. */
  permission?: PermissionCode;
  description: string;
}

export interface NavSection {
  id: string;
  label?: string;
  /** Required to see any item in the section. */
  permission?: PermissionCode;
  items: NavItem[];
}

/** Sidebar structure from PROJECT_BRIEF.md section 7. */
export const NAVIGATION: NavSection[] = [
  {
    id: 'home',
    items: [
      {
        label: 'Dashboard',
        path: '/dashboard',
        icon: LayoutDashboard,
        permission: 'DASHBOARD_VIEW',
        description: 'Team KPIs, workload, deadlines and marketing performance at a glance.',
      },
    ],
  },
  {
    id: 'my-work',
    label: 'My Work',
    items: [
      { label: 'My Tasks', path: '/my/tasks', icon: ListTodo, permission: 'TASK_VIEW', description: 'Everything assigned to you, by due date.' },
      { label: 'My Tickets', path: '/my/tickets', icon: Ticket, permission: 'TICKET_VIEW', description: 'Tickets you raised or are working on.' },
      { label: 'My Calendar', path: '/my/calendar', icon: CalendarDays, permission: 'CALENDAR_VIEW', description: 'Your deadlines, events and leave.' },
    ],
  },
  {
    id: 'work',
    label: 'Work Management',
    items: [
      { label: 'Tasks', path: '/tasks', icon: ClipboardList, permission: 'TASK_VIEW', description: 'Create, assign and track tasks across the team.' },
      { label: 'Workload', path: '/workload', icon: Gauge, permission: 'WORKLOAD_VIEW', description: 'Who is overloaded, who has capacity, and what is overdue.' },
      { label: 'Projects', path: '/projects', icon: FolderKanban, permission: 'PROJECT_VIEW', description: 'Milestones, risks and progress for every project.' },
    ],
  },
  {
    id: 'help-desk',
    label: 'Help Desk',
    items: [
      { label: 'Tickets', path: '/tickets', icon: LifeBuoy, permission: 'TICKET_VIEW', description: 'Internal help desk tickets with SLA tracking.' },
      { label: 'SLA', path: '/sla', icon: Timer, permission: 'TICKET_VIEW', description: 'SLA policies, warnings, breaches and compliance.' },
    ],
  },
  {
    id: 'collaboration',
    label: 'Collaboration',
    items: [
      { label: 'Approvals', path: '/approvals', icon: Workflow, permission: 'APPROVAL_VIEW', description: 'Requests waiting for a decision.' },
      { label: 'Announcements', path: '/announcements', icon: Megaphone, permission: 'DASHBOARD_VIEW', description: 'Company and department announcements.' },
      { label: 'Knowledge Base', path: '/knowledge-base', icon: BookOpen, permission: 'KB_VIEW', description: 'SOPs, FAQs and how-to articles.' },
      { label: 'Documents', path: '/documents', icon: FileText, permission: 'DOCUMENT_VIEW', description: 'Versioned department documents.' },
      { label: 'Team', path: '/team', icon: Users, permission: 'TEAM_VIEW', description: 'Team directory and employee profiles.' },
    ],
  },
  {
    id: 'digital-marketing',
    label: 'Digital Marketing',
    permission: 'MARKETING_VIEW',
    items: [
      { label: 'Marketing Dashboard', path: '/digital-marketing', icon: ChartLine, permission: 'MARKETING_VIEW', description: 'Executive view of SEO, leads, campaigns, backlinks and content.' },
      { label: 'SEO Rankings', path: '/digital-marketing/seo', icon: Search, permission: 'SEO_VIEW', description: 'Pages, keywords and monthly ranking history.' },
      { label: 'Marketing Targets', path: '/digital-marketing/targets', icon: Target, permission: 'TARGET_VIEW', description: 'Monthly targets versus actuals.' },
      { label: 'Email Campaigns', path: '/digital-marketing/email-campaigns', icon: Mail, permission: 'CAMPAIGN_VIEW', description: 'Zoho email campaign performance.' },
      { label: 'Paid Campaigns', path: '/digital-marketing/paid-campaigns', icon: MousePointerClick, permission: 'CAMPAIGN_VIEW', description: 'LinkedIn spend, leads and cost per lead.' },
      { label: 'Leads', path: '/digital-marketing/leads', icon: UserPlus, permission: 'LEAD_VIEW', description: 'Marketing leads by source and status.' },
      { label: 'Backlinks', path: '/digital-marketing/backlinks', icon: Link2, permission: 'BACKLINK_VIEW', description: 'Monthly backlink submissions and live links.' },
      { label: 'Content & Blog', path: '/digital-marketing/content', icon: SquarePen, permission: 'CONTENT_VIEW', description: 'Blog pipeline, publishing targets and leads.' },
      { label: 'Marketing Activities', path: '/digital-marketing/activities', icon: Repeat, permission: 'MARKETING_VIEW', description: 'Recurring marketing processes and their occurrences.' },
      { label: 'Monthly Report', path: '/digital-marketing/reports', icon: FileText, permission: 'MARKETING_VIEW', description: 'Every module against the month before, with CSV export.' },
    ],
  },
  {
    id: 'analytics',
    label: 'Analytics',
    items: [
      { label: 'Reports', path: '/reports', icon: ChartColumn, permission: 'REPORT_VIEW', description: 'Management reports with CSV export.' },
    ],
  },
  {
    id: 'administration',
    label: 'Administration',
    items: [
      { label: 'Users', path: '/admin/users', icon: UsersRound, permission: 'USER_MANAGE', description: 'Create, disable and grant permissions to users.' },
      { label: 'Departments', path: '/admin/departments', icon: Building, permission: 'DEPARTMENT_MANAGE', description: 'Departments, managers and members.' },
      { label: 'Settings', path: '/admin/settings', icon: Settings, permission: 'SETTINGS_MANAGE', description: 'Thresholds, capacity defaults and workflows.' },
      { label: 'Audit Logs', path: '/admin/audit-logs', icon: ScrollText, permission: 'AUDIT_VIEW', description: 'Who changed what, and when.' },
    ],
  },
];

interface Viewer {
  roles: readonly RoleCode[];
  permissions: readonly PermissionCode[];
}

function canSee(viewer: Viewer, permission: PermissionCode | undefined): boolean {
  return permission === undefined || hasPermission(viewer, permission);
}

/** Sections and items the viewer may see. Empty sections are dropped. */
export function visibleNavigation(viewer: Viewer): NavSection[] {
  return NAVIGATION.filter((section) => canSee(viewer, section.permission))
    .map((section) => ({ ...section, items: section.items.filter((item) => canSee(viewer, item.permission)) }))
    .filter((section) => section.items.length > 0);
}

export interface RouteItem extends NavItem {
  sectionId: string;
  /** Every permission needed to open the route: the section's and the item's own. */
  requires: PermissionCode[];
}

/** Flat list of sidebar routes. A route needs both its section's permission and its own, as the sidebar shows it. */
export const NAV_ITEMS: RouteItem[] = NAVIGATION.flatMap((section) =>
  section.items.map((item) => ({
    ...item,
    sectionId: section.id,
    requires: [...new Set([section.permission, item.permission].filter((p): p is PermissionCode => p !== undefined))],
  })),
);
