import type { TaskPriority } from '@/features/tasks/types';
import type { UserSummary } from '@/lib/api/types';

export type TicketStatus = 'NEW' | 'OPEN' | 'IN_PROGRESS' | 'WAITING_FOR_REQUESTER' | 'RESOLVED' | 'CLOSED';
/** Same values as task priorities, so the task priority indicator and labels are reused. */
export type TicketPriority = TaskPriority;
export type TicketView = 'ALL' | 'REQUESTED_BY_ME' | 'ASSIGNED_TO_ME' | 'UNASSIGNED';
export type SlaState = 'ON_TRACK' | 'WARNING' | 'BREACHED';

export const OPEN_TICKET_STATUSES: TicketStatus[] = ['NEW', 'OPEN', 'IN_PROGRESS', 'WAITING_FOR_REQUESTER'];
export const ALL_TICKET_STATUSES: TicketStatus[] = [...OPEN_TICKET_STATUSES, 'RESOLVED', 'CLOSED'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

export interface CategoryRef {
  id: number;
  name: string;
}

/** Mirrors TicketDtos.CategoryResponse; a null defaultDepartment means the requester picks the team. */
export interface TicketCategory {
  id: number;
  name: string;
  description: string | null;
  defaultDepartment: DepartmentRef | null;
}

/** Mirrors SlaCalculator.Status: computed by the server, never in the browser. */
export interface SlaStatus {
  state: SlaState;
  dueAt: string;
  remainingMinutes: number;
  elapsedPercent: number;
  /** null while open; otherwise whether the deadline was met. */
  met: boolean | null;
  paused: boolean;
}

export interface TicketSla {
  firstResponse: SlaStatus;
  resolution: SlaStatus;
  overall: SlaState;
}

/** Mirrors TicketDtos.TicketListItem. */
export interface TicketListItem {
  id: number;
  code: string;
  subject: string;
  status: TicketStatus;
  priority: TicketPriority;
  category: CategoryRef | null;
  department: DepartmentRef;
  requester: UserSummary | null;
  assignee: UserSummary | null;
  createdAt: string;
  updatedAt: string;
  sla: TicketSla;
}

export interface TicketComment {
  id: number;
  author: UserSummary | null;
  body: string;
  internal: boolean;
  edited: boolean;
  fromRequester: boolean;
  createdAt: string;
}

export interface TicketAttachment {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

export interface TicketHistoryEntry {
  id: number;
  changedBy: UserSummary | null;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  changedAt: string;
}

export interface TicketPermissions {
  canWork: boolean;
  canAssign: boolean;
  canInternalNote: boolean;
  allowedStatuses: TicketStatus[];
}

/** Mirrors TicketDtos.TicketDetail. */
export interface TicketDetail {
  id: number;
  code: string;
  subject: string;
  description: string | null;
  status: TicketStatus;
  priority: TicketPriority;
  category: CategoryRef | null;
  department: DepartmentRef;
  requester: UserSummary | null;
  assignee: UserSummary | null;
  slaPolicy: string | null;
  sla: TicketSla;
  createdAt: string;
  updatedAt: string;
  firstRespondedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  version: number;
  comments: TicketComment[];
  attachments: TicketAttachment[];
  history: TicketHistoryEntry[];
  permissions: TicketPermissions;
}

export interface MyTicketSummary {
  requestedOpen: number;
  waitingOnMe: number;
  resolvedToConfirm: number;
  assignedOpen: number;
}

export interface TicketQuery {
  search?: string;
  status?: TicketStatus[];
  priority?: TicketPriority[];
  categoryId?: number;
  departmentId?: number;
  assigneeId?: number;
  view?: TicketView;
  page?: number;
  size?: number;
  sort?: string;
}

/** Mirrors TicketRequests.CreateTicket. */
export interface CreateTicketInput {
  subject: string;
  description: string | null;
  categoryId: number;
  departmentId: number | null;
  priority: TicketPriority;
  assigneeId?: number | null;
}

/** Mirrors TicketRequests.UpdateTicket. */
export interface UpdateTicketInput {
  version: number;
  subject: string;
  description: string | null;
  categoryId: number;
  departmentId: number;
  priority: TicketPriority;
}
