import { ToolLayout, type ToolTab } from '@/features/modules/ToolLayout';

import { VENDOR_KEY, VENDOR_PATHS } from './vendor-paths';

const TABS: ToolTab[] = [
  { to: VENDOR_PATHS.vendors, label: 'Vendors', end: true },
  { to: VENDOR_PATHS.contracts, label: 'Contracts' },
  { to: VENDOR_PATHS.renewals, label: 'Upcoming renewals' },
];

/** Wraps every /tools/vendors page (the shared department tool layout); pages read `canEdit` from useTool. */
export function VendorLayout() {
  return <ToolLayout toolKey={VENDOR_KEY} tabs={TABS} />;
}
