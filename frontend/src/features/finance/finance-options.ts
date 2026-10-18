import { useApprovals } from '@/features/approvals/api';
import { hasPermission, isSuperAdmin } from '@/features/auth/permissions';
import { useAuth } from '@/features/auth/use-auth';
import { useDepartments } from '@/features/departments/api';
import { useDepartmentTools } from '@/features/modules/api';
import { useVendors, type VendorRef } from '@/features/vendors/api';
import { VENDOR_KEY } from '@/features/vendors/vendor-paths';

/**
 * Vendors to choose from (the server's /modules/vendors list): only when the user may open Vendors (its switch and
 * VENDOR_VIEW), since the vendor API answers 403 otherwise. Without it the form keeps the record's vendor as it is.
 * Blacklisted vendors take no new expenses or licences, but a record keeps showing its own vendor.
 */
export function useVendorChoices(current: VendorRef | null | undefined) {
  const tools = useDepartmentTools();
  const available = tools.data?.some((t) => t.key === VENDOR_KEY) ?? false;
  const vendors = useVendors({ size: 100, sort: 'name,asc' }, available);
  const list: VendorRef[] = (vendors.data?.content ?? [])
    .filter((v) => v.status !== 'BLACKLISTED' || v.id === current?.id)
    .map((v) => ({ id: v.id, name: v.name, status: v.status }));
  if (current && !list.some((v) => v.id === current.id)) list.unshift(current);
  return { available, vendors: list, loading: tools.isPending || (available && vendors.isPending) };
}

/** Active departments, plus the record's own even if it has since been switched off. */
export function useDepartmentChoices(currentId: number | null | undefined) {
  const departments = useDepartments();
  return {
    departments: (departments.data ?? []).filter((d) => d.status === 'ACTIVE' || d.id === currentId),
    loading: departments.isPending,
  };
}

/** Approvals an expense can link to: the newest 100 the user may see (APPROVAL_VIEW); none without it. */
export function useApprovalChoices() {
  const { user } = useAuth();
  const allowed = isSuperAdmin(user) || hasPermission(user, 'APPROVAL_VIEW');
  const approvals = useApprovals({ size: 100, sort: 'created,desc' }, allowed);
  return { allowed, approvals: allowed ? (approvals.data?.content ?? []) : [], loading: allowed && approvals.isPending };
}
