import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { LeadSource, TargetItem, TypeRef } from '../targets/api';
import type { Lead, LeadMonthCounts, LeadSummary, LinkOption } from './api';
import LeadsPage from './LeadsPage';

const originalAdapter = api.defaults.adapter;

const priya = { id: 5, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [priya],
  behindThresholdPct: 60,
};

const viewer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'LEAD_VIEW'], { id: 12, department: dm });
const editor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'LEAD_VIEW', 'LEAD_EDIT', 'TARGET_VIEW'], { id: 5, department: dm });

const websiteLeads: TypeRef = { id: 1, code: 'WEBSITE_LEADS', name: 'Website Leads', unit: 'COUNT', actualSource: 'LEADS', automatic: true };
const emailLeads: TypeRef = { id: 2, code: 'EMAIL_LEADS', name: 'Email Leads', unit: 'COUNT', actualSource: 'LEADS_BY_SOURCE', automatic: true };

function target(overrides: Partial<TargetItem>): TargetItem {
  return {
    id: 1,
    type: websiteLeads,
    month: 10,
    year: 2026,
    label: 'October 2026',
    targetValue: 250,
    actual: 200,
    actualOrigin: 'AUTOMATIC',
    achievementPct: 80,
    remaining: 50,
    status: 'IN_PROGRESS',
    thresholdPct: 60,
    owner: priya,
    department: dm,
    notes: null,
    editable: false,
    actualEditable: false,
    version: 0,
    createdAt: '2026-10-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
    ...overrides,
  };
}

/** Brief section 44: October target 250, actual 200 — organic 80, email 45, LinkedIn 35, blog 20, paid 20. */
const OCTOBER: Partial<Record<LeadSource, number>> = { ORGANIC: 80, EMAIL: 45, LINKEDIN: 35, BLOG: 20, PAID_CAMPAIGN: 20 };
const SEPTEMBER: Partial<Record<LeadSource, number>> = { ORGANIC: 70, EMAIL: 50, LINKEDIN: 35, BLOG: 10, PAID_CAMPAIGN: 15 };
const SOURCES: LeadSource[] = ['ORGANIC', 'EMAIL', 'LINKEDIN', 'PAID_CAMPAIGN', 'BLOG', 'WEBSITE', 'REFERRAL', 'OTHER'];

const withTargets: LeadSummary = {
  period: context.currentPeriod,
  comparisonPeriod: { month: 9, year: 2026, label: 'September 2026' },
  total: 200,
  comparisonTotal: 180,
  convertedPct: 12.5,
  targetsVisible: true,
  totalTarget: target({}),
  bySource: SOURCES.map((source) => ({
    source,
    leads: OCTOBER[source] ?? 0,
    comparison: SEPTEMBER[source] ?? 0,
    target: source === 'EMAIL' ? target({ id: 2, type: emailLeads, targetValue: 60, actual: 45, achievementPct: 75, remaining: 15 }) : null,
  })),
  byStatus: [
    { status: 'NEW', leads: 90 },
    { status: 'CONTACTED', leads: 50 },
    { status: 'QUALIFIED', leads: 25 },
    { status: 'CONVERTED', leads: 25 },
    { status: 'LOST', leads: 10 },
  ],
  topLinks: [{ kind: 'EMAIL_CAMPAIGN', id: 3, name: 'SAP Testing Services Outreach', leads: 30 }],
};
const withoutTargets: LeadSummary = { ...withTargets, targetsVisible: false, totalTarget: null, bySource: withTargets.bySource.map((s) => ({ ...s, target: null })) };

const trend: LeadMonthCounts[] = [
  { period: withTargets.comparisonPeriod, total: 180, bySource: Object.fromEntries(SOURCES.map((s) => [s, SEPTEMBER[s] ?? 0])) as Record<LeadSource, number> },
  { period: context.currentPeriod, total: 200, bySource: Object.fromEntries(SOURCES.map((s) => [s, OCTOBER[s] ?? 0])) as Record<LeadSource, number> },
];

function lead(overrides: Partial<Lead>): Lead {
  return {
    id: 1,
    code: 'LEAD-000101',
    name: 'Anita Rao',
    company: 'Acme Manufacturing',
    email: 'anita.rao@acme.example',
    phone: null,
    source: 'EMAIL',
    link: { kind: 'EMAIL_CAMPAIGN', id: 3, name: 'SAP Testing Services Outreach', date: '2026-10-02' },
    department: dm,
    leadDate: '2026-10-06',
    status: 'NEW',
    owner: priya,
    notes: null,
    provider: 'MANUAL',
    externalId: null,
    createdBy: priya,
    countLocked: false,
    version: 4,
    createdAt: '2026-10-06T05:00:00Z',
    updatedAt: '2026-10-06T05:00:00Z',
    ...overrides,
  };
}

const lockedLead = lead({ id: 2, code: 'LEAD-000042', name: 'Vikram Shah', source: 'ORGANIC', link: null, leadDate: '2026-07-14', status: 'QUALIFIED', countLocked: true, version: 1 });

const paidOptions: LinkOption[] = [
  { kind: 'PAID_CAMPAIGN', id: 8, name: 'SAP S/4HANA Testing Campaign', date: '2026-08-01', detail: 'LINKEDIN' },
  { kind: 'PAID_CAMPAIGN', id: 9, name: 'QA services search ads', date: '2026-09-01', detail: 'GOOGLE_ADS' },
];
const emailOptions: LinkOption[] = [{ kind: 'EMAIL_CAMPAIGN', id: 3, name: 'SAP Testing Services Outreach', date: '2026-10-02', detail: 'NEWSLETTER' }];

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/imports': () => [],
    'GET /departments': () => [{ ...dm, description: null, status: 'ACTIVE', manager: null, memberCount: 4, secondaryMemberCount: 0 }],
    'GET /marketing/leads/summary': () => withTargets,
    'GET /marketing/leads/trend': () => ({ months: trend }),
    'GET /marketing/leads': () => page([lead({}), lockedLead]),
    'GET /marketing/leads/1': () => lead({}),
    'GET /marketing/leads/2': () => lockedLead,
    'GET /marketing/leads/link-options': (config) => ((config.params as { kind: string }).kind === 'EMAIL_CAMPAIGN' ? emailOptions : paidOptions),
    ...extra,
  };
}

describe('LeadsPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the month against the Website Leads target and each source against its own', async () => {
    mockApi(routes());
    renderPage(<LeadsPage />, editor);

    const cards = await screen.findByRole('region', { name: 'Leads for October 2026' });
    const generated = within(cards).getByText('Leads generated').closest('div')!;
    expect(generated).toHaveTextContent('200');
    expect(generated).toHaveTextContent('Up 11.1% vs September');
    const website = within(cards).getByText('Website Leads target').closest('div')!.parentElement!;
    expect(website).toHaveTextContent('200of 250');
    expect(website).toHaveTextContent('80% achieved');
    expect(website).toHaveTextContent('50 remaining');
    expect(within(website).getByText('In progress')).toBeInTheDocument();
    expect(within(cards).getByText('Conversion share').closest('div')).toHaveTextContent('12.5%');

    const bySource = screen.getByRole('table', { name: 'Leads by source, October 2026' });
    const organic = within(bySource).getByRole('button', { name: 'Organic' }).closest('tr')!;
    expect(organic).toHaveTextContent('80');
    expect(within(organic).getByText('Up 10')).toHaveClass('text-status-success');
    expect(within(organic).getByText('No target')).toBeInTheDocument();
    const email = within(bySource).getByRole('button', { name: 'Email' }).closest('tr')!;
    expect(within(email).getByText('Down 5')).toHaveClass('text-status-danger');
    expect(email).toHaveTextContent('45 of 60 · 75%');
    expect(within(email).getByRole('progressbar', { name: 'Email target achievement' })).toHaveAttribute('aria-valuenow', '75');
    expect(within(screen.getByRole('list', { name: 'Top campaigns and content' })).getByText('30 leads')).toBeInTheDocument();
    expect(screen.getByRole('figure', { name: 'Leads by month and source' })).toBeInTheDocument();
  });

  it('hides targets and editing from a viewer without those permissions', async () => {
    mockApi(routes({ 'GET /marketing/leads/summary': () => withoutTargets }));
    renderPage(<LeadsPage />, viewer);

    const cards = await screen.findByRole('region', { name: 'Leads for October 2026' });
    expect(within(cards).queryByText('Website Leads target')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Leads by source, October 2026' })).queryByText('Target')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New lead' })).not.toBeInTheDocument();
  });

  it('lists the month’s leads and narrows them to a source picked in the summary', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/leads': (config) => (requests.push(config), page([lead({}), lockedLead])) }));
    renderPage(<LeadsPage />, viewer);

    const anita = (await screen.findByRole('button', { name: 'Anita Rao' })).closest('tr')!;
    expect(anita).toHaveTextContent('LEAD-000101');
    expect(anita).toHaveTextContent('SAP Testing Services Outreach');
    expect(within(anita).getByText('New')).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: 'Vikram Shah' }).closest('tr')!).getByText('(month closed)')).toBeInTheDocument();
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, sort: 'date,desc' });

    await user.click(within(screen.getByRole('table', { name: 'Leads by source, October 2026' })).getByRole('button', { name: 'LinkedIn' }));
    expect(screen.getByLabelText('Lead source')).toHaveValue('LINKEDIN');
    expect(requests.at(-1)?.params).toMatchObject({ source: ['LINKEDIN'] });

    await user.click(screen.getByLabelText('All months'));
    expect(requests.at(-1)?.params).not.toHaveProperty('month');
  });

  it('changes a lead’s status on its own, with the version', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/leads/1/status': (config) => (puts.push(config), lead({ status: 'CONTACTED', version: 5 })) }));
    renderPage(<LeadsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Anita Rao' }));
    const sheet = await screen.findByRole('dialog', { name: /Anita Rao/ });
    expect(within(sheet).getByText('SAP Testing Services Outreach')).toBeInTheDocument();
    const group = within(sheet).getByRole('group', { name: 'Change status' });
    expect(within(group).getByRole('button', { name: 'New' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(within(group).getByRole('button', { name: 'Contacted' }));
    expect(body(puts[0]!)).toEqual({ version: 4, status: 'CONTACTED' });
    expect(await within(group).findByRole('button', { name: 'Contacted' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('is read-only in the drawer for viewers', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<LeadsPage />, viewer);

    await user.click(await screen.findByRole('button', { name: 'Anita Rao' }));
    const sheet = await screen.findByRole('dialog', { name: /Anita Rao/ });
    expect(within(sheet).queryByRole('group', { name: 'Change status' })).not.toBeInTheDocument();
    expect(within(sheet).queryByRole('button', { name: 'Edit lead' })).not.toBeInTheDocument();
  });

  it('creates a lead with the campaign its source takes, checking dates first', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/leads': (config) => (posts.push(config), lead({ id: 1 })) }));
    renderPage(<LeadsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'New lead' }));
    const dialog = await screen.findByRole('dialog', { name: 'New lead' });
    expect(within(dialog).getByText('Organic leads are not linked to a campaign or content.')).toBeInTheDocument();
    await user.clear(within(dialog).getByLabelText(/Lead date/));
    await user.type(within(dialog).getByLabelText(/Lead date/), '2026-10-12');
    await user.type(within(dialog).getByLabelText('Email'), 'not-an-email');
    await user.click(within(dialog).getByRole('button', { name: 'Add lead' }));
    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument();
    expect(within(dialog).getByText('A lead cannot be dated in the future')).toBeInTheDocument();
    expect(within(dialog).getByText('Enter a valid email address')).toBeInTheDocument();

    // A LinkedIn lead is offered LinkedIn campaigns only.
    await user.selectOptions(within(dialog).getByLabelText(/Source/), 'LINKEDIN');
    const picker = await within(dialog).findByLabelText('Paid campaign');
    await within(picker).findByRole('option', { name: /SAP S\/4HANA Testing Campaign/ });
    expect(within(picker).queryByRole('option', { name: /QA services search ads/ })).not.toBeInTheDocument();

    await user.selectOptions(within(dialog).getByLabelText(/Source/), 'EMAIL');
    const emailPicker = await within(dialog).findByLabelText('Email campaign');
    await user.selectOptions(emailPicker, await within(emailPicker).findByRole('option', { name: /SAP Testing Services Outreach/ }));
    await user.type(within(dialog).getByLabelText(/Lead name/), 'Anita Rao');
    await user.clear(within(dialog).getByLabelText('Email'));
    await user.type(within(dialog).getByLabelText('Email'), 'anita.rao@acme.example');
    await user.clear(within(dialog).getByLabelText(/Lead date/));
    await user.type(within(dialog).getByLabelText(/Lead date/), '2026-10-01');
    await user.click(within(dialog).getByRole('button', { name: 'Add lead' }));
    // The campaign went out on 2 October.
    expect(await within(dialog).findByText(/The lead cannot be dated before SAP Testing Services Outreach/)).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.clear(within(dialog).getByLabelText(/Lead date/));
    await user.type(within(dialog).getByLabelText(/Lead date/), '2026-10-06');
    await user.click(within(dialog).getByRole('button', { name: 'Add lead' }));
    expect(body(posts[0]!)).toEqual({
      name: 'Anita Rao',
      company: null,
      email: 'anita.rao@acme.example',
      phone: null,
      source: 'EMAIL',
      emailCampaignId: 3,
      paidCampaignId: null,
      contentItemId: null,
      departmentId: 7,
      leadDate: '2026-10-06',
      status: 'NEW',
      ownerId: 5,
      notes: null,
    });
    // The new lead opens.
    expect(await screen.findByRole('group', { name: 'Change status' })).toBeInTheDocument();
  });

  it('keeps the date and source of a lead in a closed month, and does not offer to delete it', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/leads/2': (config) => (puts.push(config), lockedLead) }));
    renderPage(<LeadsPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Vikram Shah' }));
    const sheet = await screen.findByRole('dialog', { name: /Vikram Shah/ });
    expect(within(sheet).getByText('Month closed')).toBeInTheDocument();
    await user.click(within(sheet).getByRole('button', { name: 'Edit lead' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit LEAD-000042' });
    expect(within(dialog).getByLabelText(/Source/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/Lead date/)).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete lead' })).not.toBeInTheDocument();

    await user.type(within(dialog).getByLabelText('Phone'), '+91 98450 00000');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(body(puts[0]!)).toMatchObject({ version: 1, source: 'ORGANIC', leadDate: '2026-07-14', phone: '+91 98450 00000', status: 'QUALIFIED' });
  });
});
