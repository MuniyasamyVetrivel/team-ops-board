import type { DepartmentTool } from '@/features/modules/api';
import type { Contract, Vendor } from '@/features/vendors/api';

import { makeUser, webEmployee } from './fixtures';

export function vendorTool(canEdit: boolean): DepartmentTool {
  return { key: 'vendors', name: 'Vendors', icon: 'Store', description: 'Vendors, contacts, contracts and renewals', canEdit };
}

/** Reads and edits Vendors, but cannot see finance amounts (no FINANCE_VIEW). */
export const vendorEditor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'VENDOR_VIEW', 'VENDOR_EDIT'], { id: 14, fullName: 'Hema Rao' });

export function vendor(overrides: Partial<Vendor> = {}): Vendor {
  return {
    id: 5,
    name: 'Atlassian',
    taxId: '53 102 443 916',
    category: 'SOFTWARE',
    country: 'Australia',
    address: '341 George St, Sydney',
    website: 'https://www.atlassian.com',
    paymentTerms: 'Net 30',
    status: 'ACTIVE',
    rating: 4,
    notes: null,
    contacts: [
      { id: 11, name: 'Priya Shah', role: 'Account Executive', email: 'priya@atlassian.example', phone: '+61 2 9000 0000' },
      { id: 12, name: 'Meera Iyer', role: 'Billing', email: null, phone: null },
    ],
    contractCount: 2,
    nextRenewal: '2026-11-06',
    version: 3,
    createdAt: '2026-01-10T04:00:00Z',
    updatedAt: '2026-10-01T04:00:00Z',
    ...overrides,
  };
}

export function contract(overrides: Partial<Contract> = {}): Contract {
  return {
    id: 21,
    vendor: { id: 5, name: 'Atlassian', status: 'ACTIVE' },
    title: 'Jira Cloud Premium',
    startDate: '2026-03-01',
    endDate: '2027-02-28',
    value: 18500,
    valueHidden: false,
    currency: 'AUD',
    renewalDate: '2026-11-06',
    autoRenew: true,
    noticePeriodDays: 30,
    notes: null,
    status: 'ACTIVE',
    renewsOn: '2026-11-06',
    daysUntilRenewal: 20,
    noticeBy: '2026-10-07',
    documents: [{ fileId: 90, fileName: 'signed contract.pdf', contentType: 'application/pdf', sizeBytes: 48_000, addedBy: null, addedAt: '2026-03-01T04:00:00Z' }],
    version: 1,
    createdAt: '2026-03-01T04:00:00Z',
    updatedAt: '2026-03-01T04:00:00Z',
    ...overrides,
  };
}
