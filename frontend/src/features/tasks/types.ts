import type { UserSummary } from '@/lib/api/types';

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'IN_REVIEW' | 'COMPLETED' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
/** Computed by the server in the business time zone; closed tasks are NONE. */
export type DueState = 'OVERDUE' | 'DUE_TODAY' | 'DUE_SOON' | 'SCHEDULED' | 'NONE';
export type DueFilter = 'OVERDUE' | 'TODAY' | 'UPCOMING' | 'NO_DUE_DATE';
export type TaskView = 'ALL' | 'ASSIGNED_TO_ME' | 'CREATED_BY_ME' | 'WATCHING';

export const ACTIVE_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'IN_REVIEW'];
export const ALL_STATUSES: TaskStatus[] = [...ACTIVE_STATUSES, 'COMPLETED', 'CANCELLED'];
export const PRIORITIES: TaskPriority[] = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];

interface DepartmentRef {
  id: number;
  name: string;
  code: string;
}

/** Mirrors com.teamops.project.dto.ProjectRef. */
export interface ProjectRef {
  id: number;
  code: string;
  name: string;
  departmentId: number;
  status: string;
}

/** Mirrors com.teamops.task.dto.TaskListItem. */
export interface TaskListItem {
  id: number;
  code: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  department: DepartmentRef;
  project: ProjectRef | null;
  assignee: UserSummary | null;
  dueDate: string | null;
  dueState: DueState;
  estimatedHours: number | null;
  completedAt: string | null;
  updatedAt: string;
}

export interface TaskRef {
  id: number;
  code: string;
  title: string;
  status: TaskStatus;
}

export interface CommentResponse {
  id: number;
  author: UserSummary | null;
  body: string;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ChecklistItemResponse {
  id: number;
  content: string;
  done: boolean;
  doneBy: UserSummary | null;
  doneAt: string | null;
  position: number;
}

export interface HistoryEntry {
  id: number;
  changedBy: UserSummary | null;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  changedAt: string;
}

export interface AttachmentResponse {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedBy: UserSummary | null;
  addedAt: string;
}

export interface TaskPermissions {
  canEdit: boolean;
  canAssign: boolean;
  canCancel: boolean;
  canComment: boolean;
  canWatch: boolean;
}

/** Mirrors com.teamops.task.dto.TaskDetail. Send `version` back on edits. */
export interface TaskDetail {
  id: number;
  code: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  department: DepartmentRef;
  project: ProjectRef | null;
  assignee: UserSummary | null;
  createdBy: UserSummary | null;
  startDate: string | null;
  dueDate: string | null;
  dueState: DueState;
  estimatedHours: number | null;
  actualHours: number | null;
  completedAt: string | null;
  source: 'MANUAL' | 'MARKETING_ACTIVITY';
  createdAt: string;
  updatedAt: string;
  version: number;
  tags: string[];
  watchers: UserSummary[];
  dependencies: TaskRef[];
  checklist: ChecklistItemResponse[];
  comments: CommentResponse[];
  attachments: AttachmentResponse[];
  history: HistoryEntry[];
  permissions: TaskPermissions;
}

export interface MyTaskSummary {
  today: string;
  active: number;
  overdue: number;
  dueToday: number;
  upcoming: number;
  inProgress: number;
  blocked: number;
  completedThisWeek: number;
}

export interface TaskQuery {
  search?: string;
  status?: TaskStatus[];
  priority?: TaskPriority[];
  assigneeId?: number;
  departmentId?: number;
  projectId?: number;
  due?: DueFilter;
  view?: TaskView;
  page?: number;
  size?: number;
  sort?: string;
}

export interface CreateTaskInput {
  title: string;
  description?: string | null;
  departmentId?: number | null;
  projectId?: number | null;
  assigneeId?: number | null;
  priority?: TaskPriority;
  startDate?: string | null;
  dueDate?: string | null;
  estimatedHours?: number | null;
  tags?: string[];
}

export interface UpdateTaskInput {
  version: number;
  title: string;
  description: string | null;
  departmentId: number;
  projectId: number | null;
  priority: TaskPriority;
  startDate: string | null;
  dueDate: string | null;
  estimatedHours: number | null;
  actualHours: number | null;
  tags: string[];
}
