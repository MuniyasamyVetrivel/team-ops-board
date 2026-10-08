import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { MonthlyTargets, TargetItem, TargetTrend, TargetTypeItem, TypeRef } from './api';
import TargetsPage from './TargetsPage';

const originalAdapter = api.defaults.adapter;

const priya = { id: 5, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };

const context: MarketingContext = {
  today: '2026-10-08',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [priya],
  behindThresholdPct: 60,
};

const reader = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'TARGET_VIEW']);
const editor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'TARGET_VIEW', 'TARGET_EDIT'], { id: 5, department: { id: 7, name: 'Digital Marketing', code: 'DM' } });
const manager = makeUser(['EMPLOYEE'], [...editor.permissions, 'MARKETING_EDIT'], { id: 5, department: { id: 7, name: 'Digital Marketing', code: 'DM' } });

const websiteLeads: TypeRef = { id: 1, code: 'WEBSITE_LEADS', name: 'Website Leads', unit: 'COUNT', actualSource: 'LEADS', automatic: false };
const top10: TypeRef = { id: 10, code: 'KEYWORDS_TOP10', name: 'Keywords in Top 10', unit: 'COUNT', actualSource: 'KEYWORDS_TOP10', automatic: true };
const backlinks: TypeRef = { id: 7, code: 'BACKLINKS', name: 'Backlinks', unit: 'COUNT', actualSource: 'BACKLINKS_LIVE', automatic: false };
const prospects: TypeRef = { id: 11, code: 'MARKETING_PROSPECTS', name: 'Marketing Prospects', unit: 'COUNT', actualSource: 'MANUAL', automatic: false };

function target(overrides: Partial<TargetItem>): TargetItem {
  return {
    id: 1,
    type: websiteLeads,
    month: 10,
    year: 2026,
    label: 'October 2026',
    targetValue: 250,
    actual: 200,
    actualOrigin: 'MANUAL',
    achievementPct: 80,
    remaining: 50,
    status: 'IN_PROGRESS',
    thresholdPct: 60,
    owner: priya,
    department: { id: 7, name: 'Digital Marketing', code: 'DM' },
    notes: null,
    editable: true,
    actualEditable: true,
    version: 2,
    createdAt: '2026-10-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
    ...overrides,
  };
}

const october: MonthlyTargets = {
  period: context.currentPeriod,
  targets: [
    target({}),
    target({ id: 2, type: backlinks, targetValue: 50, actual: 22, achievementPct: 44, remaining: 28, status: 'BEHIND' }),
    target({ id: 3, type: top10, targetValue: 6, actual: 5, actualOrigin: 'AUTOMATIC', achievementPct: 83.33, remaining: 1, actualEditable: false }),
  ],
  summary: { total: 3, achieved: 0, inProgress: 2, behind: 1 },
  typesWithoutTarget: [prospects],
};

const trend: TargetTrend = {
  type: websiteLeads,
  view: 'MONTH',
  thresholdPct: 60,
  points: [
    { label: 'Aug 2026', from: { month: 8, year: 2026, label: 'August 2026' }, to: { month: 8, year: 2026, label: 'August 2026' }, months: 1, targetValue: 200, actual: 185, achievementPct: 92.5, remaining: 15, status: 'IN_PROGRESS' },
    { label: 'Sep 2026', from: { month: 9, year: 2026, label: 'September 2026' }, to: { month: 9, year: 2026, label: 'September 2026' }, months: 1, targetValue: 220, actual: 210, achievementPct: 95.45, remaining: 10, status: 'IN_PROGRESS' },
    { label: 'Nov 2026', from: { month: 11, year: 2026, label: 'November 2026' }, to: { month: 11, year: 2026, label: 'November 2026' }, months: 1, targetValue: 260, actual: null, achievementPct: null, remaining: null, status: null },
    { label: 'Dec 2026', from: { month: 12, year: 2026, label: 'December 2026' }, to: { month: 12, year: 2026, label: 'December 2026' }, months: 0, targetValue: null, actual: null, achievementPct: null, remaining: null, status: null },
  ],
};

type Handler = (config: InternalAxiosRequestConfig) => unknown;

function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/targets': (config) => {
      const { month } = config.params as { month: number };
      if (month === 9) return { ...october, period: { month: 9, year: 2026, label: 'September 2026' }, targets: [target({ id: 9, type: prospects, targetValue: 35, actual: 36, month: 9 })], typesWithoutTarget: [] };
      if (month === 11) return { ...october, period: { month: 11, year: 2026, label: 'November 2026' }, targets: [target({ id: 20, month: 11, targetValue: 260, actual: null, actualOrigin: 'NONE', achievementPct: null, remaining: 260, status: null, actualEditable: false })], summary: { total: 1, achieved: 0, inProgress: 0, behind: 0 } };
      return october;
    },
    'GET /marketing/targets/trend': () => trend,
    ...extra,
  };
}

const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

describe('TargetsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows each target as a card with progress, remaining and a labelled status', async () => {
    // The server decides per viewer whether a target is editable.
    mockApi(routes({ 'GET /marketing/targets': () => ({ ...october, targets: october.targets.map((t) => ({ ...t, editable: false, actualEditable: false })) }) }));
    renderPage(<TargetsPage />, reader);

    const leads = (await screen.findByText('Website Leads', { selector: 'span' })).closest('div.rounded-xl')!;
    expect(within(leads as HTMLElement).getByText('80% achieved')).toBeInTheDocument();
    expect(within(leads as HTMLElement).getByText('50 remaining')).toBeInTheDocument();
    expect(within(leads as HTMLElement).getByText('In progress')).toBeInTheDocument();
    expect(within(leads as HTMLElement).getByRole('progressbar', { name: 'Website Leads achievement' })).toHaveAttribute('aria-valuenow', '80');

    const auto = screen.getByText('Keywords in Top 10', { selector: 'span' }).closest('div.rounded-xl')!;
    expect(within(auto as HTMLElement).getByText(/Automatic: keywords in the top 10/)).toBeInTheDocument();

    const summary = screen.getByRole('region', { name: 'Target status for October 2026' });
    expect(within(summary).getByText('Behind', { selector: 'span' }).nextSibling).toHaveTextContent('1');

    // Readers get no editing.
    expect(screen.queryByRole('button', { name: 'Set targets' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Target types' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Edit / })).not.toBeInTheDocument();
  });

  it('lists targets in a table with achievement bars', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<TargetsPage />, reader);

    await user.click(await screen.findByRole('tab', { name: 'Table' }));
    const row = screen.getByRole('cell', { name: /Backlinks/ }).closest('tr')!;
    expect(within(row).getByRole('progressbar', { name: 'Backlinks achievement' })).toHaveAttribute('aria-valuenow', '44');
    expect(within(row).getByText('Behind')).toBeInTheDocument();
    expect(within(row).getByText('28')).toBeInTheDocument();
  });

  it('shows a future month as planned', async () => {
    mockApi(routes());
    renderPage(<TargetsPage />, reader, '/?month=11&year=2026');

    expect(await screen.findByText('Planned month')).toBeInTheDocument();
    expect(screen.getByText('Planned')).toBeInTheDocument();
    expect(screen.getByText('260 remaining')).toBeInTheDocument();
    expect(screen.getByText(/November 2026 · planned/)).toBeInTheDocument();
  });

  it('edits a target and its hand-entered actual', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/targets/1': (config) => (puts.push(config), target({ actual: 275 })) }));
    renderPage(<TargetsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Edit Website Leads target' }));
    const dialog = await screen.findByRole('dialog', { name: 'Website Leads · October 2026' });
    await user.clear(within(dialog).getByLabelText('Actual'));
    await user.type(within(dialog).getByLabelText('Actual'), '10.5');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(await within(dialog).findByText('Enter a whole number')).toBeInTheDocument();

    await user.clear(within(dialog).getByLabelText('Actual'));
    await user.type(within(dialog).getByLabelText('Actual'), '275');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(puts).toHaveLength(1);
    expect(body(puts[0]!)).toEqual({ version: 2, targetValue: 250, actualValue: 275, ownerId: 5, departmentId: 7, notes: null });
  });

  it('never asks for an automatic actual', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<TargetsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Edit Keywords in Top 10 target' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).queryByLabelText('Actual')).not.toBeInTheDocument();
    expect(within(dialog).getByText(/Calculated automatically: keywords in the top 10 \(5 so far\)/)).toBeInTheDocument();
  });

  it('sets the month’s missing targets, copying last month', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/targets/monthly': (config) => (posts.push(config), { period: context.currentPeriod, created: 1 }) }));
    renderPage(<TargetsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Set targets' }));
    const sheet = await screen.findByRole('dialog', { name: 'Set targets for October 2026' });
    expect(within(sheet).getByText('Marketing Prospects')).toBeInTheDocument();
    await user.click(await within(sheet).findByRole('button', { name: 'Copy September 2026' }));
    expect(within(sheet).getByLabelText('Marketing Prospects target')).toHaveValue('35');
    await user.click(within(sheet).getByRole('button', { name: 'Set 1 target' }));

    expect(posts).toHaveLength(1);
    expect(body(posts[0]!)).toEqual({ month: 10, year: 2026, ownerId: 5, departmentId: 7, entries: [{ typeId: 11, targetValue: 35 }] });
  });

  it('shows the monthly history with statuses in words', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/targets/trend': (config) => (requests.push(config), trend) }));
    renderPage(<TargetsPage />, reader);

    const table = await screen.findByRole('table', { name: 'Website Leads by period' });
    expect(within(table).getAllByRole('row')).toHaveLength(4);
    expect(within(within(table).getByText('Nov 2026').closest('tr')!).getByText('Planned')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ typeId: 1, view: 'MONTH', year: 2026 });

    await user.click(screen.getByRole('tab', { name: 'Quarter' }));
    expect(requests.at(-1)?.params).toMatchObject({ view: 'QUARTER' });
  });

  it('lets marketing managers manage target types; types in use keep their unit', async () => {
    const user = userEvent.setup();
    const types: TargetTypeItem[] = [
      { id: 1, code: 'WEBSITE_LEADS', name: 'Website Leads', description: null, unit: 'COUNT', actualSource: 'LEADS', leadSourceFilter: null, behindThresholdPct: null, effectiveThresholdPct: 60, automatic: false, active: true, position: 1, targetCount: 3, locked: true, version: 0 },
      { id: 30, code: 'WEBINARS', name: 'Webinars', description: null, unit: 'COUNT', actualSource: 'MANUAL', leadSourceFilter: null, behindThresholdPct: 80, effectiveThresholdPct: 80, automatic: false, active: true, position: 14, targetCount: 0, locked: false, version: 0 },
    ];
    mockApi(routes({ 'GET /marketing/target-types': () => types }));
    renderPage(<TargetsPage />, manager);

    await user.click(await screen.findByRole('button', { name: 'Target types' }));
    const sheet = await screen.findByRole('dialog', { name: 'Target types' });
    expect(await within(sheet).findByText('Entered by hand until that module is available')).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Delete Webinars' })).toBeInTheDocument();
    expect(within(sheet).queryByRole('button', { name: 'Delete Website Leads' })).not.toBeInTheDocument();

    await user.click(within(sheet).getByRole('button', { name: 'Edit Website Leads' }));
    expect(within(sheet).getByLabelText(/Unit/)).toBeDisabled();
    expect(within(sheet).getByLabelText(/Actual comes from/)).toBeDisabled();
    await user.click(within(sheet).getByRole('button', { name: 'Cancel' }));

    await user.click(within(sheet).getByRole('button', { name: 'Add target type' }));
    await user.type(within(sheet).getByLabelText(/Name/), 'Referral Leads');
    await user.selectOptions(within(sheet).getByLabelText(/Actual comes from/), 'Leads from one source');
    await user.click(within(sheet).getByRole('button', { name: 'Add type' }));
    expect(await within(sheet).findByText('Choose which lead source counts')).toBeInTheDocument();
  });
});
