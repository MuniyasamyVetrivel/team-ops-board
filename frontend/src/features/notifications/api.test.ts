import { describe, expect, it } from 'vitest';

import { notificationHref, type AppNotification } from './api';

const base: AppNotification = { id: 1, type: 'TASK_DUE_SOON', title: 'Due tomorrow', body: null, entityType: 'TASK', entityId: 42, read: false, createdAt: '2026-10-08T05:00:00Z' };

describe('notificationHref', () => {
  it('opens the record a notification is about', () => {
    expect(notificationHref(base)).toBe('/tasks?task=42');
    // Reminders for recurring activities without a task open the activity.
    expect(notificationHref({ ...base, entityType: 'MARKETING_ACTIVITY', entityId: 5 })).toBe('/digital-marketing/activities/5');
    expect(notificationHref({ ...base, entityType: 'SOMETHING_ELSE' })).toBeNull();
    expect(notificationHref({ ...base, entityId: null })).toBeNull();
  });
});
