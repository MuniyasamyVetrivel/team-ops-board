import { CircleCheck, Feather, Flame, TriangleAlert, type LucideIcon } from 'lucide-react';

import type { BadgeProps } from '@/components/ui/badge';

import type { WorkloadLevel } from './api';

/** Brief section 9 levels with semantic colours (section 62) and an icon, so colour is never the only signal. */
export const LEVEL_META: Record<WorkloadLevel, { label: string; range: string; tone: NonNullable<BadgeProps['tone']>; bar: string; icon: LucideIcon }> = {
  LOW: { label: 'Low', range: '0–40%', tone: 'neutral', bar: 'bg-status-neutral', icon: Feather },
  NORMAL: { label: 'Normal', range: '41–70%', tone: 'success', bar: 'bg-status-success', icon: CircleCheck },
  HIGH: { label: 'High', range: '71–100%', tone: 'warning', bar: 'bg-status-warning', icon: TriangleAlert },
  OVERLOADED: { label: 'Overloaded', range: '101%+', tone: 'danger', bar: 'bg-status-danger', icon: Flame },
};
