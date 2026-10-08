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
  /** Implementation phase from docs/implementation-plan.md; shown on placeholder pages until built. */
  phase: number;
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
        phase: 6,
      },
    ],
  },
  {
    id: 'my-work',
    label: 'My Work',
    items: [
      { label: 'My Tasks', path: '/my/tasks', icon: ListTodo, permission: 'TASK_VIEW', description: 'Everything assigned to you, by due date.', phase: 5 },
      { label: 'My Tickets', path: '/my/tickets', icon: Ticket, permission: 'TICKET_VIEW', description: 'Tickets you raised or are working on.', phase: 7 },
      { label: 'My Calendar', path: '/my/calendar', icon: CalendarDays, permission: 'CALENDAR_VIEW', description: 'Your deadlines, events and leave.', phase: 8 },
    ],
  },
  {
    id: 'work',
    label: 'Work Management',
    items: [
      { label: 'Tasks', path: '/tasks', icon: ClipboardList, permission: 'TASK_VIEW', description: 'Create, assign and track tasks across the team.', phase: 5 },
      { label: 'Workload', path: '/workload', icon: Gauge, permission: 'WORKLOAD_VIEW', description: 'Who is overloaded, who has capacity, and what is overdue.', phase: 5 },
      { label: 'Projects', path: '/projects', icon: FolderKanban, permission: 'PROJECT_VIEW', description: 'Milestones, risks and progress for every project.', phase: 8 },
    ],
  },
  {
    id: 'help-desk',
    label: 'Help Desk',
    items: [
      { label: 'Tickets', path: '/tickets', icon: LifeBuoy, permission: 'TICKET_VIEW', description: 'Internal help desk tickets with SLA tracking.', phase: 7 },
      { label: 'SLA', path: '/sla', icon: Timer, permission: 'TICKET_VIEW', description: 'SLA policies, warnings, breaches and compliance.', phase: 7 },
    ],
  },
  {
    id: 'collaboration',
    label: 'Collaboration',
    items: [
      { label: 'Approvals', path: '/approvals', icon: Workflow, permission: 'APPROVAL_VIEW', description: 'Requests waiting for a decision.', phase: 8 },
      { label: 'Announcements', path: '/announcements', icon: Megaphone, permission: 'DASHBOARD_VIEW', description: 'Company and department announcements.', phase: 8 },
      { label: 'Knowledge Base', path: '/knowledge-base', icon: BookOpen, permission: 'KB_VIEW', description: 'SOPs, FAQs and how-to articles.', phase: 8 },
      { label: 'Documents', path: '/documents', icon: FileText, permission: 'DOCUMENT_VIEW', description: 'Versioned department documents.', phase: 8 },
      { label: 'Team', path: '/team', icon: Users, permission: 'TEAM_VIEW', description: 'Team directory and employee profiles.', phase: 4 },
    ],
  },
  {
    id: 'digital-marketing',
    label: 'Digital Marketing',
    permission: 'MARKETING_VIEW',
    items: [
      { label: 'Marketing Dashboard', path: '/digital-marketing', icon: ChartLine, permission: 'MARKETING_VIEW', description: 'Executive view of SEO, leads, campaigns, backlinks and content.', phase: 19 },
      { label: 'SEO Rankings', path: '/digital-marketing/seo', icon: Search, permission: 'SEO_VIEW', description: 'Pages, keywords and monthly ranking history.', phase: 10 },
      { label: 'Marketing Targets', path: '/digital-marketing/targets', icon: Target, permission: 'TARGET_VIEW', description: 'Monthly targets versus actuals.', phase: 12 },
      { label: 'Email Campaigns', path: '/digital-marketing/email-campaigns', icon: Mail, permission: 'CAMPAIGN_VIEW', description: 'Zoho email campaign performance.', phase: 14 },
      { label: 'Paid Campaigns', path: '/digital-marketing/paid-campaigns', icon: MousePointerClick, permission: 'CAMPAIGN_VIEW', description: 'LinkedIn spend, leads and cost per lead.', phase: 15 },
      { label: 'Leads', path: '/digital-marketing/leads', icon: UserPlus, permission: 'LEAD_VIEW', description: 'Marketing leads by source and status.', phase: 16 },
      { label: 'Backlinks', path: '/digital-marketing/backlinks', icon: Link2, permission: 'BACKLINK_VIEW', description: 'Monthly backlink submissions and live links.', phase: 17 },
      { label: 'Content & Blog', path: '/digital-marketing/content', icon: SquarePen, permission: 'CONTENT_VIEW', description: 'Blog pipeline, publishing targets and leads.', phase: 18 },
      { label: 'Marketing Activities', path: '/digital-marketing/activities', icon: Repeat, permission: 'MARKETING_VIEW', description: 'Recurring marketing processes and their occurrences.', phase: 13 },
    ],
  },
  {
    id: 'analytics',
    label: 'Analytics',
    items: [
      { label: 'Reports', path: '/reports', icon: ChartColumn, permission: 'REPORT_VIEW', description: 'Management reports with CSV export.', phase: 20 },
    ],
  },
  {
    id: 'administration',
    label: 'Administration',
    items: [
      { label: 'Users', path: '/admin/users', icon: UsersRound, permission: 'USER_MANAGE', description: 'Create, disable and grant permissions to users.', phase: 4 },
      { label: 'Departments', path: '/admin/departments', icon: Building, permission: 'DEPARTMENT_MANAGE', description: 'Departments, managers and members.', phase: 4 },
      { label: 'Settings', path: '/admin/settings', icon: Settings, permission: 'SETTINGS_MANAGE', description: 'Thresholds, capacity defaults and workflows.', phase: 21 },
      { label: 'Audit Logs', path: '/admin/audit-logs', icon: ScrollText, permission: 'AUDIT_VIEW', description: 'Who changed what, and when.', phase: 21 },
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
