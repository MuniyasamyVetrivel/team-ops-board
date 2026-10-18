import { ToolLayout, type ToolTab } from '@/features/modules/ToolLayout';

import { FINANCE_KEY, FINANCE_PATHS } from './finance-paths';

const TABS: ToolTab[] = [
  { to: FINANCE_PATHS.dashboard, label: 'Dashboard', end: true },
  { to: FINANCE_PATHS.licences, label: 'Tools and licences' },
  { to: FINANCE_PATHS.expenses, label: 'Expenses' },
  { to: FINANCE_PATHS.budgets, label: 'Budgets' },
  { to: FINANCE_PATHS.rates, label: 'Exchange rates' },
];

/** Wraps every /tools/finance page (the shared department tool layout); pages read `canEdit` from useTool. */
export function FinanceLayout() {
  return <ToolLayout toolKey={FINANCE_KEY} tabs={TABS} />;
}
