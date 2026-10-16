import type { Allocation, BenchDashboard, BenchEntry, Consultant, ConsultantEmployee, Engagement } from '@/features/bench/api';
import type { DepartmentTool } from '@/features/modules/api';

export function benchTool(canEdit: boolean): DepartmentTool {
  return { key: 'bench', name: 'Bench Management', icon: 'Users', description: 'Consultants, client engagements, allocations and the bench', canEdit };
}

export function consultantEmployee(overrides: Partial<ConsultantEmployee> = {}): ConsultantEmployee {
  return {
    id: 40,
    employeeCode: 'TRI-0101',
    fullName: 'Arjun Rao',
    designation: 'Senior Test Engineer',
    department: { id: 3, name: 'Delivery', code: 'DELIVERY' },
    workEmail: 'arjun.rao@tritusa.example',
    user: null,
    ...overrides,
  };
}

export function consultant(overrides: Partial<Consultant> = {}): Consultant {
  return {
    id: 7,
    employeeId: 40,
    employee: consultantEmployee(),
    experienceYears: 6.5,
    location: 'Chennai',
    availableFrom: '2026-09-01',
    employmentStatus: 'ACTIVE',
    status: 'PARTIALLY_ALLOCATED',
    allocatedPercent: 50,
    skills: ['Tosca', 'SAP S/4HANA'],
    certifications: [
      { id: 1, name: 'Tosca Automation Specialist', issuer: 'Tricentis', issuedOn: '2025-03-01', expiresOn: '2027-03-01', credentialId: 'AS1-99', expired: false },
    ],
    notes: null,
    version: 2,
    createdAt: '2026-09-01T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z',
    ...overrides,
  };
}

export function allocation(overrides: Partial<Allocation> = {}): Allocation {
  return {
    id: 12,
    consultant: { id: 7, employeeCode: 'TRI-0101', fullName: 'Arjun Rao', user: null },
    engagement: { id: 5, name: 'S/4HANA regression', client: { id: 3, name: 'Acme Corp' } },
    role: 'Test automation lead',
    startDate: '2026-09-01',
    endDate: '2026-11-05',
    allocationPercent: 50,
    billable: true,
    status: 'ACTIVE',
    daysUntilEnd: 19,
    notes: null,
    version: 1,
    createdAt: '2026-09-01T04:00:00Z',
    updatedAt: '2026-09-01T04:00:00Z',
    ...overrides,
  };
}

export function engagement(overrides: Partial<Engagement> = {}): Engagement {
  return {
    id: 5,
    client: { id: 3, name: 'Acme Corp' },
    name: 'S/4HANA regression',
    location: 'Sydney',
    startDate: '2026-09-01',
    endDate: '2026-12-31',
    status: 'ACTIVE',
    accountManager: null,
    notes: null,
    version: 1,
    createdAt: '2026-08-20T04:00:00Z',
    updatedAt: '2026-08-20T04:00:00Z',
    ...overrides,
  };
}

export function benchEntry(overrides: Partial<BenchEntry> = {}): BenchEntry {
  return {
    consultantId: 8,
    employee: consultantEmployee({ id: 41, employeeCode: 'TRI-0102', fullName: 'Meera Iyer', designation: 'Performance Tester' }),
    location: 'Pune',
    skills: ['JMeter', 'LoadRunner'],
    availableFrom: '2026-08-01',
    status: 'ON_BENCH',
    allocatedPercent: 0,
    benchPercent: 100,
    benchSince: '2026-08-20',
    daysOnBench: 59,
    ...overrides,
  };
}

export function dashboard(overrides: Partial<BenchDashboard> = {}): BenchDashboard {
  return {
    asOf: '2026-10-17',
    byStatus: [
      { status: 'ALLOCATED', count: 3 },
      { status: 'PARTIALLY_ALLOCATED', count: 1 },
      { status: 'ON_BENCH', count: 1 },
      { status: 'LEAVE_OF_ABSENCE', count: 0 },
      { status: 'NOTICE_PERIOD', count: 0 },
      { status: 'LEFT', count: 0 },
    ],
    capacityConsultants: 5,
    capacityFte: 5,
    allocatedFte: 3.5,
    billableFte: 3,
    nonBillableFte: 0.5,
    benchFte: 1.5,
    utilisationPercent: 70,
    billablePercent: 60,
    nonBillablePercent: 10,
    onBenchNow: 2,
    unallocated: 1,
    averageDaysOnBench: 31.5,
    longestDaysOnBench: 59,
    longestOnBench: [benchEntry()],
    upcomingRollOffCount: 1,
    upcomingRollOffs: [allocation()],
    benchSkills: [
      { skill: 'JMeter', consultants: 2 },
      { skill: 'Tosca', consultants: 1 },
    ],
    ...overrides,
  };
}
