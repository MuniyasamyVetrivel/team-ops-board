import type { SlaStatus, TicketDetail, TicketListItem } from '@/features/tickets/types';

const requester = { id: 4, fullName: 'Karthik Raj', email: 'karthik.raj@teamops.local', jobTitle: 'Frontend Developer', status: 'ACTIVE' as const };
const agent = { id: 11, fullName: 'Vignesh Raman', email: 'vignesh.raman@teamops.local', jobTitle: 'System Administrator', status: 'ACTIVE' as const };

export function slaStatus(overrides: Partial<SlaStatus> = {}): SlaStatus {
  return { state: 'ON_TRACK', dueAt: '2026-10-08T09:00:00Z', remainingMinutes: 200, elapsedPercent: 17, met: null, paused: false, ...overrides };
}

export function ticketItem(overrides: Partial<TicketListItem> = {}): TicketListItem {
  return {
    id: 42,
    code: 'TKT-000042',
    subject: 'Laptop will not boot',
    status: 'OPEN',
    priority: 'URGENT',
    category: { id: 1, name: 'IT Support' },
    department: { id: 1, name: 'IT', code: 'IT' },
    requester,
    assignee: agent,
    createdAt: '2026-10-08T05:00:00Z',
    updatedAt: '2026-10-08T05:30:00Z',
    sla: {
      firstResponse: slaStatus({ met: true, remainingMinutes: 30 }),
      resolution: slaStatus({ state: 'WARNING', remainingMinutes: 45, elapsedPercent: 81 }),
      overall: 'WARNING',
    },
    ...overrides,
  };
}

export function ticketDetail(overrides: Partial<TicketDetail> = {}): TicketDetail {
  return {
    id: 42,
    code: 'TKT-000042',
    subject: 'Laptop will not boot',
    description: 'Black screen after the update.',
    status: 'OPEN',
    priority: 'URGENT',
    category: { id: 1, name: 'IT Support' },
    department: { id: 1, name: 'IT', code: 'IT' },
    requester,
    assignee: agent,
    slaPolicy: 'Urgent',
    sla: {
      firstResponse: slaStatus({ met: true, remainingMinutes: 30 }),
      resolution: slaStatus({ state: 'WARNING', remainingMinutes: 45, elapsedPercent: 81 }),
      overall: 'WARNING',
    },
    createdAt: '2026-10-08T05:00:00Z',
    updatedAt: '2026-10-08T05:30:00Z',
    firstRespondedAt: '2026-10-08T05:30:00Z',
    resolvedAt: null,
    closedAt: null,
    version: 2,
    comments: [
      { id: 1, author: agent, body: 'Disk looks faulty', internal: true, edited: false, fromRequester: false, createdAt: '2026-10-08T05:20:00Z' },
      { id: 2, author: agent, body: 'Can you bring it to IT?', internal: false, edited: false, fromRequester: false, createdAt: '2026-10-08T05:30:00Z' },
    ],
    attachments: [],
    history: [],
    permissions: { canWork: true, canAssign: true, canInternalNote: true, allowedStatuses: ['IN_PROGRESS', 'WAITING_FOR_REQUESTER', 'RESOLVED', 'CLOSED'] },
    ...overrides,
  };
}
