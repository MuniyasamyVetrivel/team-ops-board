import { describe, expect, it } from 'vitest';

import { LEVEL_META } from './levels';

describe('LEVEL_META', () => {
  it('gives every level the brief’s range, a label and an icon (never colour alone)', () => {
    expect(Object.fromEntries(Object.entries(LEVEL_META).map(([level, meta]) => [level, [meta.label, meta.range, meta.tone]]))).toEqual({
      LOW: ['Low', '0–40%', 'neutral'],
      NORMAL: ['Normal', '41–70%', 'success'],
      HIGH: ['High', '71–100%', 'warning'],
      OVERLOADED: ['Overloaded', '101%+', 'danger'],
    });
    for (const meta of Object.values(LEVEL_META)) {
      expect(meta.icon).toBeTruthy();
    }
  });
});
