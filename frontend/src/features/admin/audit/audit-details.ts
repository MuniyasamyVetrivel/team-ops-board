/**
 * Turns an audit entry's details document into what the viewer shows: before/after rows, added/removed lists and
 * any other facts. Shapes written by the backend:
 * - field edits: {changes: {field: {from, to}}} (AuditChanges and the automatic record changes), optionally with extra
 *   keys such as closedMonth. A change may carry flags: ref (the values are ids of that record type) with
 *   fromLabel/toLabel (names of users and departments), finance (a money amount), hidden (a money amount the viewer
 *   may not see) and redacted (a secret: no values are stored);
 * - money inside business events that the viewer may not see is blanked and listed in hiddenFields;
 * - access changes: {before: {roles, permissions}, after: {roles, permissions}};
 * - role permission changes: {role, added: [...], removed: [...]};
 * - everything else: a flat object of facts (e.g. {code, name, steps}).
 */
export interface ChangeRow {
  field: string;
  before: string;
  after: string;
  /** Values are not shown: a finance amount the viewer may not see, or a secret that is never stored. */
  masked?: 'finance' | 'secret';
}

export interface DescribeOptions {
  /** False hides money amounts even if the server sent them (the server already hides them; this is a second guard). */
  canSeeFinance?: boolean;
}

export const HIDDEN_VALUE = 'Hidden';

export interface AuditDetails {
  changes: ChangeRow[];
  added: string[];
  removed: string[];
  facts: [string, string][];
}

type JsonObject = Record<string, unknown>;

const isObject = (value: unknown): value is JsonObject => typeof value === 'object' && value !== null && !Array.isArray(value);

/** "closedMonth" → "Closed month", "requiresAmount" → "Requires amount"; keys with dots (setting keys) stay as they are. */
export function fieldLabel(key: string): string {
  if (key.includes('.')) return key;
  const words = key
    .replace(/_/g, ' ')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Display text for a JSON value; empty values show as "—". */
export function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (Array.isArray(value)) return value.length === 0 ? '—' : value.map(displayValue).join(', ');
  if (isObject(value)) {
    return Object.entries(value)
      .map(([k, v]) => `${fieldLabel(k)}: ${displayValue(v)}`)
      .join('; ');
  }
  return String(value);
}

const strings = (value: unknown): string[] => (Array.isArray(value) ? value.map((v) => String(v)) : []);

/** "TAG" → "Tag", "MARKETING_LEAD" → "Marketing lead". */
export function typeLabel(type: string): string {
  const words = type.replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** A changed value: a referenced record's name when known, "Tag #5" when only its id is, otherwise the value. */
function changeValue(value: unknown, label: unknown, ref: unknown): string {
  if (label !== undefined && label !== null) return displayValue(label);
  if (typeof ref === 'string' && (typeof value === 'number' || Array.isArray(value))) {
    const ids = Array.isArray(value) ? value : [value];
    return ids.length === 0 ? '—' : ids.map((id) => `${typeLabel(ref)} #${String(id)}`).join(', ');
  }
  return displayValue(value);
}

function changeRow(field: string, change: JsonObject, options: DescribeOptions): ChangeRow {
  const label = fieldLabel(field);
  if (change.redacted === true) return { field: label, before: '—', after: 'Changed (not shown)', masked: 'secret' };
  if (change.hidden === true || (change.finance === true && options.canSeeFinance === false)) {
    return { field: label, before: HIDDEN_VALUE, after: HIDDEN_VALUE, masked: 'finance' };
  }
  return {
    field: label,
    before: changeValue(change.from, change.fromLabel, change.ref),
    after: changeValue(change.to, change.toLabel, change.ref),
  };
}

const isChange = (value: unknown): value is JsonObject =>
  isObject(value) && ('from' in value || 'to' in value || value.hidden === true || value.redacted === true);

export function describeDetails(details: unknown, options: DescribeOptions = {}): AuditDetails {
  const result: AuditDetails = { changes: [], added: [], removed: [], facts: [] };
  if (details === null || details === undefined) return result;
  if (!isObject(details)) {
    result.facts.push(['Details', displayValue(details)]);
    return result;
  }
  const consumed = new Set<string>();

  if (isObject(details.changes)) {
    consumed.add('changes');
    for (const [field, change] of Object.entries(details.changes)) {
      if (isChange(change)) {
        result.changes.push(changeRow(field, change, options));
      } else {
        result.facts.push([fieldLabel(field), displayValue(change)]);
      }
    }
  }

  if (isObject(details.before) && isObject(details.after)) {
    consumed.add('before');
    consumed.add('after');
    const before = details.before;
    const after = details.after;
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
    for (const key of keys) {
      const from = displayValue(before[key]);
      const to = displayValue(after[key]);
      if (from !== to) result.changes.push({ field: fieldLabel(key), before: from, after: to });
    }
  }

  if (Array.isArray(details.added) || Array.isArray(details.removed)) {
    consumed.add('added');
    consumed.add('removed');
    result.added = strings(details.added);
    result.removed = strings(details.removed);
  }

  const hidden = Array.isArray(details.hiddenFields) ? details.hiddenFields.map(String) : [];
  consumed.add('hiddenFields');
  for (const [key, value] of Object.entries(details)) {
    if (!consumed.has(key)) result.facts.push([fieldLabel(key), hidden.includes(key) ? HIDDEN_VALUE : displayValue(value)]);
  }
  if (hidden.length > 0) result.facts.push(['Finance amounts', 'Hidden: you need Finance access to see them']);
  return result;
}

/** One line for the table: what changed, in brief. */
export function summarizeDetails(details: unknown, options: DescribeOptions = {}): string {
  const described = describeDetails(details, options);
  const parts: string[] = [];
  if (described.changes.length > 0) {
    const first = described.changes[0]!;
    parts.push(`${first.field}: ${first.before} → ${first.after}`);
    if (described.changes.length > 1) parts.push(`+${described.changes.length - 1} more`);
  }
  if (described.added.length > 0) parts.push(`Added ${described.added.join(', ')}`);
  if (described.removed.length > 0) parts.push(`Removed ${described.removed.join(', ')}`);
  if (parts.length === 0 && described.facts.length > 0) {
    parts.push(
      described.facts
        .slice(0, 2)
        .map(([k, v]) => `${k}: ${v}`)
        .join(' · '),
    );
  }
  return parts.join(' · ');
}
