import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router';

import { FinanceLayout } from '@/features/finance/FinanceLayout';

/** One Finance Tracker page inside the real layout (tool check, tab bar, `canEdit`); an empty path is the index. */
export function financeRoutes(path: string, page: ReactElement): ReactElement {
  return (
    <Routes>
      <Route path="/tools/finance" element={<FinanceLayout />}>
        {path ? <Route path={path} element={page} /> : <Route index element={page} />}
      </Route>
    </Routes>
  );
}
