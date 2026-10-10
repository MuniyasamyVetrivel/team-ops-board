/**
 * Turns an audit entry's details document into what the viewer shows: before/after rows, added/removed lists and
 * any other facts. Shapes written by the backend:
 * - field edits: {changes: {field: {from, to}}} (AuditChanges), optionally with extra keys such as closedMonth;
 * - access changes: {before: {roles, permissions}, after: {roles, permissions}};
 * - role permission changes: {role, added: [...], removed: [...]};
 * - everything else: a flat object of facts (e.g. {code, name, steps}).
 */
export interface ChangeRow {
  field: string;
  before: string;
  after: string;
}

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

export function describeDetails(details: unknown): AuditDetails {
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
      if (isObject(change) && ('from' in change || 'to' in change)) {
        result.changes.push({ field: fieldLabel(field), before: displayValue(change.from), after: displayValue(change.to) });
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

  for (const [key, value] of Object.entries(details)) {
    if (!consumed.has(key)) result.facts.push([fieldLabel(key), displayValue(value)]);
  }
  return result;
}

/** One line for the table: what changed, in brief. */
export function summarizeDetails(details: unknown): string {
  const described = describeDetails(details);
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
