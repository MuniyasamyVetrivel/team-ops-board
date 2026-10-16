import {
  Boxes,
  Briefcase,
  Building,
  Handshake,
  Landmark,
  LayoutGrid,
  PiggyBank,
  Receipt,
  Store,
  Truck,
  UserCheck,
  Users,
  Wallet,
  Wrench,
  type LucideIcon,
} from 'lucide-react';

/**
 * Icons a module may name in its ModuleDefinition. Only these are bundled; a module with another icon name adds it
 * here (docs/adding-a-module.md). Unknown names fall back to a generic grid.
 */
const MODULE_ICONS: Record<string, LucideIcon> = {
  Boxes,
  Briefcase,
  Building,
  Handshake,
  Landmark,
  PiggyBank,
  Receipt,
  Store,
  Truck,
  UserCheck,
  Users,
  Wallet,
  Wrench,
};

export function moduleIcon(name: string): LucideIcon {
  return MODULE_ICONS[name] ?? LayoutGrid;
}

/** The route of a department tool's pages. */
export function toolPath(key: string): string {
  return `/tools/${key}`;
}
