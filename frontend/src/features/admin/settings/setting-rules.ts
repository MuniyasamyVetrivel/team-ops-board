import type { SettingItem } from './api';

/** Decimal settings keep at most two decimal places (mirrors SettingDefinition.MAX_SCALE). */
const MAX_DECIMALS = 2;

/**
 * Mirrors SettingDefinition.normalize: a number, whole for INTEGER settings, at most two decimals, within the
 * setting's range. Returns the message to show, or null when the value is acceptable.
 */
export function settingValueError(setting: Pick<SettingItem, 'label' | 'valueType' | 'min' | 'max' | 'unit'>, raw: string): string | null {
  const text = raw.trim();
  if (text === '') return `${setting.label} is required`;
  if (!/^-?\d+(\.\d+)?$/.test(text)) return `${setting.label} must be a number`;
  const decimals = text.includes('.') ? text.split('.')[1]!.replace(/0+$/, '').length : 0;
  if (setting.valueType === 'INTEGER' && decimals > 0) return `${setting.label} must be a whole number`;
  if (decimals > MAX_DECIMALS) return `${setting.label} can have at most ${MAX_DECIMALS} decimal places`;
  const value = Number(text);
  if (value < setting.min || value > setting.max) {
    return `${setting.label} must be between ${setting.min} and ${setting.max} ${setting.unit}`;
  }
  return null;
}

/** Groups settings by their group, keeping the server's order. */
export function groupSettings(settings: readonly SettingItem[]): [string, SettingItem[]][] {
  const groups = new Map<string, SettingItem[]>();
  for (const setting of settings) {
    groups.set(setting.group, [...(groups.get(setting.group) ?? []), setting]);
  }
  return [...groups.entries()];
}
