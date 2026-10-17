import { toolPath } from '@/features/modules/module-icons';

/** The module key (VendorModule.KEY); its pages live under /tools/vendors. */
export const VENDOR_KEY = 'vendors';

const ROOT = toolPath(VENDOR_KEY);

/** The Vendors pages, in the order of the module's tab bar. Contracts open from the URL (?contract=12). */
export const VENDOR_PATHS = {
  vendors: ROOT,
  contracts: `${ROOT}/contracts`,
  renewals: `${ROOT}/renewals`,
} as const;

export function vendorPath(id: number): string {
  return `${ROOT}/${id}`;
}

export function contractHref(id: number): string {
  return `${VENDOR_PATHS.contracts}?contract=${id}`;
}
