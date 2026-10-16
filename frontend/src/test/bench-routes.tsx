import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router';

import { BenchLayout } from '@/features/bench/BenchLayout';

/** One Bench Management page inside the real layout (tool check, tab bar, `canEdit`); an empty path is the index. */
export function benchRoutes(path: string, page: ReactElement): ReactElement {
  return (
    <Routes>
      <Route path="/tools/bench" element={<BenchLayout />}>
        {path ? <Route path={path} element={page} /> : <Route index element={page} />}
      </Route>
    </Routes>
  );
}
