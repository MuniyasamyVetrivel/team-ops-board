import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { DepartmentSummary } from '@/features/auth/types';
import type { ImportDefinition, ImportEndpoint } from '@/features/marketing/api';
import type { VendorRef } from '@/features/vendors/api';
import { vendorKeys } from '@/features/vendors/api';
import { api } from '@/lib/api/client';
import { downloadFile } from '@/lib/api/download';
import { cleanParams, serializeParams, type PageResponse, type UserSummary } from '@/lib/api/types';

/** Everything lives under the module's path, where the server enforces the module switch and FINANCE_VIEW / FINANCE_EDIT. */
const BASE = '/modules/finance';

/** Mirrors com.teamops.finance.entity.BillingCycle. */
export type BillingCycle = 'MONTHLY' | 'YEARLY' | 'ONE_OFF';
/** Mirrors com.teamops.finance.entity.LicenceStatus: set by people. */
export type LicenceStatus = 'ACTIVE' | 'TRIAL' | 'CANCELLED';
/** Mirrors com.teamops.finance.entity.ExpenseCategory. */
export type ExpenseCategory = 'SOFTWARE' | 'HARDWARE' | 'RECRUITMENT' | 'MARKETING' | 'TRAVEL' | 'TRAINING' | 'OFFICE' | 'OTHER';

export const BILLING_CYCLES: BillingCycle[] = ['MONTHLY', 'YEARLY', 'ONE_OFF'];
export const LICENCE_STATUSES: LicenceStatus[] = ['ACTIVE', 'TRIAL', 'CANCELLED'];
export const EXPENSE_CATEGORIES: ExpenseCategory[] = ['SOFTWARE', 'HARDWARE', 'RECRUITMENT', 'MARKETING', 'TRAVEL', 'TRAINING', 'OFFICE', 'OTHER'];

/** Mirrors FinanceContextController.FinanceContext: today's business date, AUD, the default currency and its choices. */
export interface FinanceContext {
  today: string;
  baseCurrency: string;
  defaultCurrency: string;
  currencies: string[];
}

// --- exchange rates ---------------------------------------------------------------------------------------------

/** Mirrors FinanceDtos.ExchangeRateDto. One unit of `currency` is `rateToAud` AUD from `effectiveFrom`; `inForce` is computed. */
export interface ExchangeRate {
  id: number;
  currency: string;
  effectiveFrom: string;
  rateToAud: number;
  unitsPerAud: number;
  source: string | null;
  inForce: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors FinanceDtos.SaveExchangeRate. */
export interface SaveExchangeRateInput {
  currency: string;
  effectiveFrom: string;
  rateToAud: number;
  source: string | null;
  version?: number;
}

/** Mirrors FinanceDtos.Conversion: an amount in AUD at the rate in force on `date`. */
export interface Conversion {
  amount: number;
  currency: string;
  date: string;
  rate: number;
  rateFrom: string | null;
  aud: number;
}

// --- tools and licences -----------------------------------------------------------------------------------------

/** Mirrors FinanceDtos.AudRate. */
export interface AudRate {
  rate: number;
  effectiveFrom: string | null;
}

/**
 * Mirrors FinanceDtos.LicenceDto. Seats used, free and utilisation, under-use, annual costs (and their AUD at today's
 * rate) and days to renewal are computed by the server on today's business date.
 */
export interface Licence {
  id: number;
  name: string;
  vendorId: number | null;
  vendor: VendorRef | null;
  owner: UserSummary | null;
  department: DepartmentSummary | null;
  seatsPaid: number | null;
  seatsUsed: number;
  seatsFree: number | null;
  utilisationPercent: number | null;
  underUsed: boolean;
  overAssigned: boolean;
  assignedUsers: UserSummary[];
  billingCycle: BillingCycle;
  cost: number | null;
  currency: string;
  annualCost: number | null;
  annualCostAud: number | null;
  audRate: AudRate | null;
  startDate: string | null;
  renewalDate: string | null;
  daysUntilRenewal: number | null;
  paymentMethod: string | null;
  cardLast4: string | null;
  status: LicenceStatus;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors FinanceDtos.SaveLicence. `assignedUserIds` replaces the assigned users. */
export interface SaveLicenceInput {
  name: string;
  vendorId: number | null;
  ownerId: number | null;
  departmentId: number | null;
  seatsPaid: number | null;
  billingCycle: BillingCycle;
  cost: number | null;
  currency: string;
  startDate: string | null;
  renewalDate: string | null;
  paymentMethod: string | null;
  cardLast4: string | null;
  status: LicenceStatus;
  assignedUserIds: number[];
  notes: string | null;
  version?: number;
}

// --- expenses ---------------------------------------------------------------------------------------------------

/** Mirrors FinanceDtos.ApprovalRef (never its amount). */
export interface ApprovalRef {
  id: number;
  code: string;
  title: string;
  status: string;
}

/** Mirrors FinanceDtos.ReceiptDto. */
export interface Receipt {
  fileId: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  addedAt: string;
}

/**
 * Mirrors FinanceDtos.ExpenseDto. `amount` is the total paid (GST included). The AUD rate, its date and `amountAud`
 * are stored at the expense date; `amountExGst` and `gstAud` are computed.
 */
export interface Expense {
  id: number;
  expenseDate: string;
  category: ExpenseCategory;
  vendorId: number | null;
  vendor: VendorRef | null;
  description: string;
  amount: number;
  gst: number | null;
  amountExGst: number | null;
  currency: string;
  audRate: number;
  audRateDate: string | null;
  amountAud: number;
  gstAud: number | null;
  department: DepartmentSummary | null;
  receipt: Receipt | null;
  approval: ApprovalRef | null;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors FinanceDtos.SaveExpense. */
export interface SaveExpenseInput {
  expenseDate: string;
  category: ExpenseCategory;
  vendorId: number | null;
  description: string;
  amount: number;
  gst: number | null;
  currency: string;
  departmentId: number | null;
  approvalId: number | null;
  notes: string | null;
  version?: number;
}

// --- budgets ----------------------------------------------------------------------------------------------------

/** Mirrors FinanceDtos.BudgetDto: in AUD; remaining, used %, over-budget and over-by are computed. */
export interface Budget {
  id: number;
  department: DepartmentSummary;
  year: number;
  month: number;
  amount: number;
  actual: number;
  remaining: number;
  usedPercent: number | null;
  overBudget: boolean;
  overBy: number;
  notes: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors FinanceDtos.SaveBudget. The department and month can't change on an update (BUDGET_PERIOD_FIXED). */
export interface SaveBudgetInput {
  departmentId: number;
  year: number;
  month: number;
  amount: number;
  notes: string | null;
  version?: number;
}

/** Mirrors FinanceDtos.BudgetLine: a department with a budget or spend; a null department is spend without one. */
export interface BudgetLine {
  department: DepartmentSummary | null;
  budgetId: number | null;
  budget: number | null;
  actual: number;
  remaining: number | null;
  usedPercent: number | null;
  overBudget: boolean;
  overBy: number | null;
}

/** Mirrors FinanceDtos.BudgetSummary. */
export interface BudgetSummary {
  year: number;
  month: number;
  lines: BudgetLine[];
  totalBudget: number;
  totalActual: number;
  totalActualBudgeted: number;
  overBudgetDepartments: number;
}

// --- dashboard --------------------------------------------------------------------------------------------------

/** Mirrors FinanceDtos.FinanceDashboard; every amount in AUD, every figure computed by the server. */
export interface FinanceDashboard {
  asOf: string;
  year: number;
  month: number;
  monthSpendAud: number;
  previousMonthSpendAud: number;
  changePercent: number | null;
  expenseCount: number;
  trend: { year: number; month: number; totalAud: number }[];
  byDepartment: { department: DepartmentSummary | null; totalAud: number }[];
  byCategory: { category: ExpenseCategory; totalAud: number; expenses: number }[];
  monthBudgetAud: number;
  overBudgetDepartments: number;
  upcomingRenewalCount: number;
  upcomingRenewals: Licence[];
  underUsedCount: number;
  underUsedLicences: Licence[];
  annualisedSoftwareCostAud: number;
  licencesWithoutRate: number;
}

// --- queries ----------------------------------------------------------------------------------------------------

interface Paging {
  page?: number;
  size?: number;
  sort?: string;
}

export interface LicenceQuery extends Paging {
  search?: string;
  vendorId?: number;
  departmentId?: number;
  status?: LicenceStatus;
  billingCycle?: BillingCycle;
  renewalWithinDays?: number;
}

export interface ExpenseQuery extends Paging {
  search?: string;
  category?: ExpenseCategory;
  departmentId?: number;
  vendorId?: number;
  approvalId?: number;
  from?: string;
  to?: string;
}

export interface RateQuery extends Paging {
  currency?: string;
}

export interface BudgetQuery extends Paging {
  year?: number;
  month?: number;
  departmentId?: number;
}

export const financeKeys = {
  all: ['finance'] as const,
  context: () => [...financeKeys.all, 'context'] as const,
  dashboard: (year?: number, month?: number) => [...financeKeys.all, 'dashboard', year ?? null, month ?? null] as const,
  licences: () => [...financeKeys.all, 'licences'] as const,
  licenceList: (query: LicenceQuery) => [...financeKeys.licences(), 'list', query] as const,
  licence: (id: number) => [...financeKeys.licences(), 'detail', id] as const,
  expenses: () => [...financeKeys.all, 'expenses'] as const,
  expenseList: (query: ExpenseQuery) => [...financeKeys.expenses(), 'list', query] as const,
  expense: (id: number) => [...financeKeys.expenses(), 'detail', id] as const,
  rates: () => [...financeKeys.all, 'rates'] as const,
  rateList: (query: RateQuery) => [...financeKeys.rates(), 'list', query] as const,
  currentRates: () => [...financeKeys.rates(), 'current'] as const,
  conversion: (amount: number, currency: string, date: string) => [...financeKeys.rates(), 'convert', amount, currency, date] as const,
  budgets: () => [...financeKeys.all, 'budgets'] as const,
  budgetList: (query: BudgetQuery) => [...financeKeys.budgets(), 'list', query] as const,
  budgetSummary: (year: number, month: number) => [...financeKeys.budgets(), 'summary', year, month] as const,
  imports: () => [...financeKeys.all, 'imports'] as const,
};

/**
 * The dashboard, budgets (actual spend), licences (AUD at today's rate), the currency choices and the vendor page all
 * read each other's records, so every change refreshes the whole module and the vendors' linked records; the edited
 * record's detail cache is set from the response first.
 */
function useInvalidateFinance() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: financeKeys.all });
    void queryClient.invalidateQueries({ queryKey: [...vendorKeys.vendors()] });
  };
}

function exportUrl(path: string, query: Record<string, unknown>): string {
  const search = serializeParams(cleanParams(query));
  return `${BASE}/${path}/export${search ? `?${search}` : ''}`;
}

export function useFinanceContext() {
  return useQuery({
    queryKey: financeKeys.context(),
    queryFn: async () => (await api.get<FinanceContext>(`${BASE}/context`)).data,
    staleTime: 60_000,
  });
}

export function useFinanceDashboard(year?: number, month?: number) {
  return useQuery({
    queryKey: financeKeys.dashboard(year, month),
    queryFn: async () => (await api.get<FinanceDashboard>(`${BASE}/dashboard`, { params: cleanParams({ year, month }) })).data,
    placeholderData: keepPreviousData,
  });
}

// --- licences ---------------------------------------------------------------------------------------------------

export function useLicences(query: LicenceQuery, enabled = true) {
  return useQuery({
    queryKey: financeKeys.licenceList(query),
    queryFn: async () => (await api.get<PageResponse<Licence>>(`${BASE}/licences`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useLicence(id: number | null) {
  return useQuery({
    queryKey: financeKeys.licence(id ?? 0),
    queryFn: async () => (await api.get<Licence>(`${BASE}/licences/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveLicence(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (input: SaveLicenceInput) =>
      id === null ? (await api.post<Licence>(`${BASE}/licences`, input)).data : (await api.put<Licence>(`${BASE}/licences/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(financeKeys.licence(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteLicence() {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/licences/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportLicences(query: Omit<LicenceQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  return downloadFile(exportUrl('licences', query), 'licences.csv');
}

// --- expenses ---------------------------------------------------------------------------------------------------

export function useExpenses(query: ExpenseQuery, enabled = true) {
  return useQuery({
    queryKey: financeKeys.expenseList(query),
    queryFn: async () => (await api.get<PageResponse<Expense>>(`${BASE}/expenses`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
    enabled,
  });
}

export function useExpense(id: number | null) {
  return useQuery({
    queryKey: financeKeys.expense(id ?? 0),
    queryFn: async () => (await api.get<Expense>(`${BASE}/expenses/${id}`)).data,
    enabled: id !== null,
  });
}

export function useSaveExpense(id: number | null) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (input: SaveExpenseInput) =>
      id === null ? (await api.post<Expense>(`${BASE}/expenses`, input)).data : (await api.put<Expense>(`${BASE}/expenses/${id}`, input)).data,
    onSuccess: (saved) => {
      queryClient.setQueryData(financeKeys.expense(saved.id), saved);
      invalidate();
    },
  });
}

export function useDeleteExpense() {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/expenses/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportExpenses(query: Omit<ExpenseQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  return downloadFile(exportUrl('expenses', query), 'expenses.csv');
}

/** Attaches (replacing any earlier one) or removes the receipt; the expense's cache is set from the response. */
export function useExpenseReceipt(expenseId: number) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateFinance();
  const replace = (saved: Expense) => {
    queryClient.setQueryData(financeKeys.expense(saved.id), saved);
    invalidate();
  };
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return (await api.post<Expense>(`${BASE}/expenses/${expenseId}/receipt`, form)).data;
    },
    onSuccess: replace,
  });
  const remove = useMutation({
    mutationFn: async () => (await api.delete<Expense>(`${BASE}/expenses/${expenseId}/receipt`)).data,
    onSuccess: replace,
  });
  return { upload, remove };
}

/** Always a download (the server sends it as an attachment). */
export function downloadReceipt(expenseId: number, receipt: Receipt): Promise<void> {
  return downloadFile(`${BASE}/expenses/${expenseId}/receipt`, receipt.fileName);
}

// --- exchange rates ---------------------------------------------------------------------------------------------

export function useExchangeRates(query: RateQuery) {
  return useQuery({
    queryKey: financeKeys.rateList(query),
    queryFn: async () => (await api.get<PageResponse<ExchangeRate>>(`${BASE}/exchange-rates`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

/** The rate in force today for each currency that has one. */
export function useCurrentRates() {
  return useQuery({
    queryKey: financeKeys.currentRates(),
    queryFn: async () => (await api.get<ExchangeRate[]>(`${BASE}/exchange-rates/current`)).data,
  });
}

/**
 * The AUD equivalent the server would store for an expense (the rate in force on its date). Disabled until the form
 * holds a valid amount, currency and date; 400 NO_EXCHANGE_RATE when there is no rate yet.
 */
export function useConversion(amount: number | null, currency: string, date: string) {
  const enabled = amount !== null && /^[A-Z]{3}$/.test(currency) && /^\d{4}-\d{2}-\d{2}$/.test(date);
  return useQuery({
    queryKey: financeKeys.conversion(amount ?? 0, currency, date),
    queryFn: async () => (await api.get<Conversion>(`${BASE}/exchange-rates/convert`, { params: { amount, currency, date } })).data,
    enabled,
    retry: false,
    staleTime: 30_000,
  });
}

export function useSaveRate(id: number | null) {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (input: SaveExchangeRateInput) =>
      id === null
        ? (await api.post<ExchangeRate>(`${BASE}/exchange-rates`, input)).data
        : (await api.put<ExchangeRate>(`${BASE}/exchange-rates/${id}`, input)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteRate() {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/exchange-rates/${id}`);
    },
    onSuccess: invalidate,
  });
}

export function exportRates(query: Omit<RateQuery, 'page' | 'size' | 'sort'>): Promise<void> {
  return downloadFile(exportUrl('exchange-rates', query), 'exchange-rates.csv');
}

// --- budgets ----------------------------------------------------------------------------------------------------

export function useBudgetSummary(year: number, month: number) {
  return useQuery({
    queryKey: financeKeys.budgetSummary(year, month),
    queryFn: async () => (await api.get<BudgetSummary>(`${BASE}/budgets/summary`, { params: { year, month } })).data,
    placeholderData: keepPreviousData,
  });
}

/** The month's budget records (notes and versions for editing); the summary carries the figures. */
export function useBudgets(query: BudgetQuery) {
  return useQuery({
    queryKey: financeKeys.budgetList(query),
    queryFn: async () => (await api.get<PageResponse<Budget>>(`${BASE}/budgets`, { params: cleanParams(query) })).data,
    placeholderData: keepPreviousData,
  });
}

export function useSaveBudget(id: number | null) {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (input: SaveBudgetInput) =>
      id === null ? (await api.post<Budget>(`${BASE}/budgets`, input)).data : (await api.put<Budget>(`${BASE}/budgets/${id}`, input)).data,
    onSuccess: invalidate,
  });
}

export function useDeleteBudget() {
  const invalidate = useInvalidateFinance();
  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`${BASE}/budgets/${id}`);
    },
    onSuccess: invalidate,
  });
}

// --- CSV imports ------------------------------------------------------------------------------------------------

/** `licences` and `exchange-rates`, through the shared import flow (validate, review, commit). */
export const FINANCE_IMPORTS: ImportEndpoint = {
  path: (type) => `${BASE}/imports/${type}`,
  invalidate: [financeKeys.all],
};

export const LICENCE_IMPORT = 'licences';
export const RATE_IMPORT = 'exchange-rates';

/** The imports the user may run (FINANCE_EDIT). */
export function useFinanceImportDefinitions(enabled: boolean) {
  return useQuery({
    queryKey: financeKeys.imports(),
    queryFn: async () => (await api.get<ImportDefinition[]>(`${BASE}/imports`)).data,
    staleTime: 5 * 60_000,
    enabled,
  });
}
