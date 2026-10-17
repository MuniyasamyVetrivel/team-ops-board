import type { ReactElement } from 'react';
import { Route, Routes } from 'react-router';

import { VendorLayout } from '@/features/vendors/VendorLayout';

/** One Vendors page inside the real layout (tool check, tab bar, `canEdit`); an empty path is the index. */
export function vendorRoutes(path: string, page: ReactElement): ReactElement {
  return (
    <Routes>
      <Route path="/tools/vendors" element={<VendorLayout />}>
        {path ? <Route path={path} element={page} /> : <Route index element={page} />}
      </Route>
    </Routes>
  );
}
