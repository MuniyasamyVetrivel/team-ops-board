import type { TaskDetail, TaskListItem } from '@/features/tasks/types';

export function taskItem(overrides: Partial<TaskListItem> = {}): TaskListItem {
  return {
    id: 42,
    code: 'TSK-000042',
    title: 'Fix contact form validation',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    department: { id: 5, name: 'Web Development', code: 'WEBDEV' },
    project: null,
    assignee: { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: 'Frontend Developer', status: 'ACTIVE' },
    dueDate: '2026-10-03',
    dueState: 'OVERDUE',
    estimatedHours: 6,
    completedAt: null,
    updatedAt: '2026-10-06T10:00:00Z',
    ...overrides,
  };
}

export function taskDetail(overrides: Partial<TaskDetail> = {}): TaskDetail {
  return {
    id: 42,
    code: 'TSK-000042',
    title: 'Fix contact form validation',
    description: 'Validate email and phone.',
    status: 'IN_PROGRESS',
    priority: 'HIGH',
    department: { id: 5, name: 'Web Development', code: 'WEBDEV' },
    project: null,
    assignee: { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: 'Frontend Developer', status: 'ACTIVE' },
    createdBy: null,
    startDate: null,
    dueDate: '2026-10-03',
    dueState: 'OVERDUE',
    estimatedHours: 6,
    actualHours: null,
    completedAt: null,
    source: 'MANUAL',
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-06T10:00:00Z',
    version: 3,
    tags: ['webdev'],
    watchers: [],
    dependencies: [],
    checklist: [],
    comments: [],
    attachments: [],
    history: [],
    permissions: { canEdit: true, canAssign: false, canCancel: false, canComment: true, canWatch: true },
    ...overrides,
  };
}
