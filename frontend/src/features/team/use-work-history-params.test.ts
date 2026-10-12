import { describe, expect, it } from 'vitest';

import { readWorkParams, toWorkQuery } from './use-work-history-params';

describe('work history URL parameters', () => {
  it('reads defaults and ignores values it does not know', () => {
    expect(readWorkParams(new URLSearchParams('range=NEXT_YEAR&dateField=SOMEDAY&page=-2'))).toMatchObject({
      kind: 'tasks',
      status: 'ALL',
      dateField: 'COMPLETED',
      range: '',
      sort: 'updated,desc',
      page: 0,
    });
  });

  it('sends a preset or a custom range with its date field, and the status only to the list', () => {
    const preset = readWorkParams(new URLSearchParams('work=tickets&status=overdue&range=LAST_MONTH&dateField=DUE&group=2&priority=HIGH&q=vpn'));
    expect(toWorkQuery(preset, true)).toEqual({ status: ['OVERDUE'], dateField: 'DUE', preset: 'LAST_MONTH', from: undefined, to: undefined, groupId: 2, priority: ['HIGH'], search: 'vpn' });
    expect(toWorkQuery(preset, false).status).toBeUndefined();

    const custom = readWorkParams(new URLSearchParams('range=CUSTOM&from=2026-09-10&to=2026-09-20'));
    expect(toWorkQuery(custom, true)).toMatchObject({ dateField: 'COMPLETED', preset: undefined, from: '2026-09-10', to: '2026-09-20' });
    expect(toWorkQuery(readWorkParams(new URLSearchParams('dateField=DUE')), true).dateField).toBeUndefined();
  });
});
