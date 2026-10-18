import { toolPath } from '@/features/modules/module-icons';

/** The module key (FinanceModule.KEY); its pages live under /tools/finance. */
export const FINANCE_KEY = 'finance';

const ROOT = toolPath(FINANCE_KEY);

/**
 * The Finance Tracker pages, in the order of the module's tab bar. Licences and expenses open from the URL
 * (?licence=12, ?expense=40), from renewal reminders, search and the vendor page.
 */
export const FINANCE_PATHS = {
  dashboard: ROOT,
  licences: `${ROOT}/licences`,
  expenses: `${ROOT}/expenses`,
  budgets: `${ROOT}/budgets`,
  rates: `${ROOT}/exchange-rates`,
} as const;

export function licenceHref(id: number): string {
  return `${FINANCE_PATHS.licences}?licence=${id}`;
}

export function expenseHref(id: number): string {
  return `${FINANCE_PATHS.expenses}?expense=${id}`;
}
