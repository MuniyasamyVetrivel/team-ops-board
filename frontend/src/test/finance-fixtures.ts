import type { BudgetSummary, Expense, ExchangeRate, FinanceContext, FinanceDashboard, Licence } from '@/features/finance/api';
import type { DepartmentTool } from '@/features/modules/api';

import { makeUser, webEmployee } from './fixtures';

export function financeTool(canEdit: boolean): DepartmentTool {
  return { key: 'finance', name: 'Finance Tracker', icon: 'Wallet', description: 'Tools and licences, expenses, budgets and exchange rates', canEdit };
}

/** Reads the Finance Tracker (FINANCE_VIEW) and changes nothing; no Vendors, no settings. */
export const financeViewer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'FINANCE_VIEW'], { id: 15, fullName: 'Divya Menon' });

export function financeContext(overrides: Partial<FinanceContext> = {}): FinanceContext {
  return { today: '2026-10-19', baseCurrency: 'AUD', defaultCurrency: 'INR', currencies: ['INR', 'AUD', 'USD', 'SGD'], ...overrides };
}

export function licence(overrides: Partial<Licence> = {}): Licence {
  return {
    id: 7,
    name: 'Jira Software',
    vendorId: 5,
    vendor: { id: 5, name: 'Atlassian', status: 'ACTIVE' },
    owner: { id: 3, fullName: 'Priya Shah', email: 'priya@teamops.local', jobTitle: 'IT Lead', status: 'ACTIVE' },
    department: { id: 2, name: 'Engineering', code: 'ENG' },
    seatsPaid: 10,
    seatsUsed: 6,
    seatsFree: 4,
    utilisationPercent: 60,
    underUsed: true,
    overAssigned: false,
    assignedUsers: [
      { id: 3, fullName: 'Priya Shah', email: 'priya@teamops.local', jobTitle: 'IT Lead', status: 'ACTIVE' },
      { id: 4, fullName: 'Arjun Rao', email: 'arjun@teamops.local', jobTitle: 'Developer', status: 'ACTIVE' },
    ],
    billingCycle: 'MONTHLY',
    cost: 120,
    currency: 'USD',
    annualCost: 1440,
    annualCostAud: 2160,
    audRate: { rate: 1.5, effectiveFrom: '2026-10-01' },
    startDate: '2026-01-01',
    renewalDate: '2026-11-06',
    daysUntilRenewal: 18,
    paymentMethod: 'Corporate Amex',
    cardLast4: '4242',
    status: 'ACTIVE',
    notes: null,
    version: 2,
    createdAt: '2026-01-01T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z',
    ...overrides,
  };
}

export function expense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 40,
    expenseDate: '2026-10-02',
    category: 'TRAVEL',
    vendorId: 9,
    vendor: { id: 9, name: 'Qantas', status: 'ACTIVE' },
    description: 'Flights to Sydney',
    amount: 1000,
    gst: 90.91,
    amountExGst: 909.09,
    currency: 'SGD',
    audRate: 1.15,
    audRateDate: '2026-09-30',
    amountAud: 1150,
    gstAud: 104.55,
    department: { id: 2, name: 'Engineering', code: 'ENG' },
    receipt: { fileId: 77, fileName: 'flight receipt.pdf', contentType: 'application/pdf', sizeBytes: 52_000, addedAt: '2026-10-02T04:00:00Z' },
    approval: { id: 12, code: 'APR-000012', title: 'Client workshop travel', status: 'APPROVED' },
    notes: null,
    version: 1,
    createdAt: '2026-10-02T04:00:00Z',
    updatedAt: '2026-10-02T04:00:00Z',
    ...overrides,
  };
}

export function rate(overrides: Partial<ExchangeRate> = {}): ExchangeRate {
  return {
    id: 3,
    currency: 'USD',
    effectiveFrom: '2026-10-01',
    rateToAud: 1.5,
    unitsPerAud: 0.66666667,
    source: 'RBA',
    inForce: true,
    version: 0,
    createdAt: '2026-10-01T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z',
    ...overrides,
  };
}

export function budgetSummary(overrides: Partial<BudgetSummary> = {}): BudgetSummary {
  return {
    year: 2026,
    month: 10,
    lines: [
      { department: { id: 2, name: 'Engineering', code: 'ENG' }, budgetId: 31, budget: 1000, actual: 1250, remaining: 0, usedPercent: 125, overBudget: true, overBy: 250 },
      { department: { id: 6, name: 'HR', code: 'HR' }, budgetId: 32, budget: 2000, actual: 500, remaining: 1500, usedPercent: 25, overBudget: false, overBy: 0 },
      { department: { id: 8, name: 'Sales', code: 'SALES' }, budgetId: null, budget: null, actual: 300, remaining: null, usedPercent: null, overBudget: false, overBy: null },
      { department: null, budgetId: null, budget: null, actual: 75, remaining: null, usedPercent: null, overBudget: false, overBy: null },
    ],
    totalBudget: 3000,
    totalActual: 2125,
    totalActualBudgeted: 1750,
    overBudgetDepartments: 1,
    ...overrides,
  };
}

export function financeDashboard(overrides: Partial<FinanceDashboard> = {}): FinanceDashboard {
  return {
    asOf: '2026-10-19',
    year: 2026,
    month: 10,
    monthSpendAud: 2125,
    previousMonthSpendAud: 1700,
    changePercent: 25,
    expenseCount: 9,
    trend: Array.from({ length: 12 }, (_, i) => ({ year: i < 2 ? 2025 : 2026, month: ((i + 10) % 12) + 1, totalAud: i === 11 ? 2125 : 1000 + i * 50 })),
    byDepartment: [
      { department: { id: 2, name: 'Engineering', code: 'ENG' }, totalAud: 1250 },
      { department: null, totalAud: 75 },
    ],
    byCategory: [
      { category: 'TRAVEL', totalAud: 1150, expenses: 2 },
      { category: 'SOFTWARE', totalAud: 975, expenses: 7 },
    ],
    monthBudgetAud: 3000,
    overBudgetDepartments: 1,
    upcomingRenewalCount: 3,
    upcomingRenewals: [licence()],
    underUsedCount: 1,
    underUsedLicences: [licence({ id: 8, name: 'Figma', utilisationPercent: 40, seatsUsed: 2, seatsPaid: 5 })],
    annualisedSoftwareCostAud: 48_000,
    licencesWithoutRate: 0,
    ...overrides,
  };
}
