import { describe, expect, it } from 'vitest';

import { notificationHref, type AppNotification } from './api';

const base: AppNotification = { id: 1, type: 'TASK_DUE_SOON', title: 'Due tomorrow', body: null, entityType: 'TASK', entityId: 42, read: false, createdAt: '2026-10-08T05:00:00Z' };

describe('notificationHref', () => {
  it('opens the record a notification is about', () => {
    expect(notificationHref(base)).toBe('/tasks?task=42');
    // Reminders for recurring activities without a task open the activity.
    expect(notificationHref({ ...base, entityType: 'MARKETING_ACTIVITY', entityId: 5 })).toBe('/digital-marketing/activities/5');
    // Birthday, anniversary, important day and event reminders open the record.
    expect(notificationHref({ ...base, type: 'OCCASION_REMINDER', entityType: 'EMPLOYEE', entityId: 40 })).toBe('/celebrations/employees?employee=40');
    expect(notificationHref({ ...base, type: 'OCCASION_REMINDER', entityType: 'IMPORTANT_DAY', entityId: 4 })).toBe('/celebrations/important-days?day=4');
    expect(notificationHref({ ...base, type: 'OCCASION_REMINDER', entityType: 'MARKETING_EVENT', entityId: 9 })).toBe('/celebrations/events?event=9');
    expect(notificationHref({ ...base, type: 'BENCH_ROLL_OFF', entityType: 'ALLOCATION', entityId: 12 })).toBe('/tools/bench/allocations?allocation=12');
    expect(notificationHref({ ...base, type: 'CONTRACT_RENEWAL', entityType: 'VENDOR_CONTRACT', entityId: 4 })).toBe('/tools/vendors/contracts?contract=4');
    expect(notificationHref({ ...base, entityType: 'SOMETHING_ELSE' })).toBeNull();
    expect(notificationHref({ ...base, entityId: null })).toBeNull();
  });
});
