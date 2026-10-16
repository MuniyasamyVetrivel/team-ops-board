import type { RecycleBinItem } from './api';

/** The item's name as people know it, or "Marketing target #12" when it has none. */
export function itemName(item: Pick<RecycleBinItem, 'label' | 'entityTypeLabel' | 'entityId'>): string {
  return item.label?.trim() || `${item.entityTypeLabel} #${item.entityId}`;
}

/** Same rule as the server: case and surrounding spaces don't matter. */
export function confirmationMatches(typed: string, expected: string): boolean {
  return typed.trim().toLowerCase() === expected.trim().toLowerCase();
}

/** "Tonight", "Tomorrow" or "In 12 days", and how urgent that is (7 days or less is soon). */
export function autoDeleteLabel(daysUntilPurge: number): { label: string; tone: 'danger' | 'warning' | 'neutral' } {
  const label = daysUntilPurge <= 0 ? 'Tonight' : daysUntilPurge === 1 ? 'Tomorrow' : `In ${daysUntilPurge} days`;
  const tone = daysUntilPurge <= 1 ? 'danger' : daysUntilPurge <= 7 ? 'warning' : 'neutral';
  return { label, tone };
}
