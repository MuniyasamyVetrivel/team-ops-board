import { describe, expect, it } from 'vitest';

import { autoDeleteLabel, confirmationMatches, itemName } from './bin-rules';

describe('recycle bin rules', () => {
  it('names an item by its label, else its type and id', () => {
    expect(itemName({ label: ' TSK-000042 ', entityTypeLabel: 'Task', entityId: 42 })).toBe('TSK-000042');
    expect(itemName({ label: null, entityTypeLabel: 'Marketing target', entityId: 12 })).toBe('Marketing target #12');
  });

  it('matches the confirmation like the server: case and surrounding spaces ignored', () => {
    expect(confirmationMatches(' tsk-000042 ', 'TSK-000042')).toBe(true);
    expect(confirmationMatches('TSK-00004', 'TSK-000042')).toBe(false);
    expect(confirmationMatches('', 'TSK-000042')).toBe(false);
  });

  it('labels the automatic delete and how urgent it is', () => {
    expect(autoDeleteLabel(0)).toEqual({ label: 'Tonight', tone: 'danger' });
    expect(autoDeleteLabel(1)).toEqual({ label: 'Tomorrow', tone: 'danger' });
    expect(autoDeleteLabel(7)).toEqual({ label: 'In 7 days', tone: 'warning' });
    expect(autoDeleteLabel(88)).toEqual({ label: 'In 88 days', tone: 'neutral' });
  });
});
