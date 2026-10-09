import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { InternalAxiosRequestConfig } from 'axios';
import { afterEach, describe, expect, it } from 'vitest';

import { api } from '@/lib/api/client';
import { makeUser, webEmployee } from '@/test/fixtures';
import { mockApi, renderPage } from '@/test/render';

import type { MarketingContext } from '../api';
import type { TargetItem, TypeRef } from '../targets/api';
import type { ContentAttachment, ContentItem, ContentMonth, ContentSummary, ContentTrendMonth } from './api';
import ContentPage from './ContentPage';

const originalAdapter = api.defaults.adapter;

const kavya = { id: 5, fullName: 'Kavya Suresh', email: 'kavya.suresh@teamops.local', jobTitle: 'Content Writer', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const context: MarketingContext = {
  today: '2026-10-09',
  currentPeriod: { month: 10, year: 2026, label: 'October 2026' },
  years: [2027, 2026, 2025],
  owners: [kavya],
  behindThresholdPct: 60,
};

const viewer = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'CONTENT_VIEW'], { id: 12, department: dm });
const editor = makeUser(['EMPLOYEE'], [...webEmployee.permissions, 'MARKETING_VIEW', 'CONTENT_VIEW', 'CONTENT_EDIT', 'TARGET_VIEW'], { id: 5, department: dm });

const blogsType: TypeRef = { id: 8, code: 'BLOGS_PUBLISHED', name: 'Blogs Published', unit: 'COUNT', actualSource: 'BLOGS_PUBLISHED', automatic: true };
const blogLeadsType: TypeRef = { id: 3, code: 'BLOG_LEADS', name: 'Blog Leads', unit: 'COUNT', actualSource: 'LEADS_BY_SOURCE', automatic: true };

function target(overrides: Partial<TargetItem>): TargetItem {
  return {
    id: 1,
    type: blogsType,
    month: 10,
    year: 2026,
    label: 'October 2026',
    targetValue: 12,
    actual: 9,
    actualOrigin: 'AUTOMATIC',
    achievementPct: 75,
    remaining: 3,
    status: 'IN_PROGRESS',
    thresholdPct: 60,
    owner: kavya,
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

const month = (m: number, label: string, f: Partial<ContentMonth>): ContentMonth => ({
  period: { month: m, year: 2026, label },
  plannedBlogs: 0,
  publishedBlogs: 0,
  publishedAll: 0,
  refreshed: 0,
  leads: 0,
  ...f,
});

/** Brief section 48: October target 12, published 9, remaining 3, leads 45. */
const october = month(10, 'October 2026', { plannedBlogs: 12, publishedBlogs: 9, publishedAll: 10, refreshed: 1, leads: 45 });
const september = month(9, 'September 2026', { plannedBlogs: 11, publishedBlogs: 11, publishedAll: 12, leads: 40 });

const summary: ContentSummary = {
  current: october,
  comparison: september,
  targetsVisible: true,
  blogTarget: { targetValue: 12, remaining: 3, target: target({}) },
  blogLeadsTarget: target({ id: 2, type: blogLeadsType, targetValue: 25, actual: 20, achievementPct: 80, remaining: 5 }),
  publishedByType: [
    { contentType: 'BLOG', published: 9 },
    { contentType: 'CASE_STUDY', published: 1 },
  ],
  pipeline: [
    { status: 'IDEA', items: 3 },
    { status: 'PLANNED', items: 4 },
    { status: 'IN_PROGRESS', items: 2 },
    { status: 'DRAFT', items: 2 },
    { status: 'PUBLISHED', items: 31 },
    { status: 'UPDATED', items: 1 },
  ],
  topContent: [{ id: 1, title: 'SAP S/4HANA regression testing: a practical guide', contentType: 'BLOG', url: null, organicTraffic: 900, ctaClicks: 20, leads: 12 }],
};
const viewerSummary: ContentSummary = { ...summary, targetsVisible: false, blogTarget: null, blogLeadsTarget: null };

const trend: ContentTrendMonth[] = [
  { figures: september, targetValue: 12, remaining: 1, achievementPct: 91.67 },
  { figures: october, targetValue: 12, remaining: 3, achievementPct: 75 },
];

function item(overrides: Partial<ContentItem>): ContentItem {
  return {
    id: 1,
    title: 'SAP S/4HANA regression testing: a practical guide',
    url: 'https://www.example.com/blog/sap-regression',
    contentType: 'BLOG',
    status: 'PUBLISHED',
    author: kavya,
    owner: kavya,
    plannedDate: '2026-10-02',
    publicationDate: '2026-10-02',
    refreshedDate: null,
    targetKeyword: null,
    targetKeywordText: 'sap regression testing',
    targetPage: null,
    organicTraffic: 900,
    ctaClicks: 20,
    notes: null,
    leads: 12,
    attachments: 1,
    publicationLocked: false,
    refreshLocked: false,
    createdBy: kavya,
    version: 3,
    createdAt: '2026-09-20T05:00:00Z',
    updatedAt: '2026-10-02T05:00:00Z',
    ...overrides,
  };
}

const draft = item({ id: 2, title: 'Test data management for SAP', status: 'DRAFT', url: null, publicationDate: null, leads: 0, attachments: 0, version: 0 });
const oldPost = item({ id: 3, title: 'Banking QA compliance', publicationDate: '2026-06-10', plannedDate: '2026-06-10', publicationLocked: true, leads: 0, attachments: 0, version: 1 });

const outline: ContentAttachment = { fileId: 41, fileName: 'outline.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', sizeBytes: 20_480, addedBy: kavya, addedAt: '2026-10-01T05:00:00Z' };

const page = <T,>(content: T[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
const body = (config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as Record<string, unknown>;

type Handler = (config: InternalAxiosRequestConfig) => unknown;
function routes(extra: Record<string, Handler> = {}): Record<string, Handler> {
  return {
    'GET /marketing/context': () => context,
    'GET /marketing/content/summary': () => summary,
    'GET /marketing/content/trend': () => ({ targetsVisible: true, months: trend }),
    'GET /marketing/content': () => page([item({}), draft, oldPost]),
    'GET /marketing/content/1': () => item({}),
    'GET /marketing/content/2': () => draft,
    'GET /marketing/content/3': () => oldPost,
    'GET /marketing/content/1/attachments': () => [outline],
    'GET /marketing/content/2/attachments': () => [],
    'GET /marketing/content/3/attachments': () => [],
    ...extra,
  };
}

describe('ContentPage', () => {
  afterEach(() => {
    api.defaults.adapter = originalAdapter;
  });

  it('shows the blog target with published, remaining, leads and planned for the month', async () => {
    mockApi(routes());
    renderPage(<ContentPage />, editor);

    const cards = await screen.findByRole('region', { name: 'Blog target for October 2026' });
    expect(within(cards).getByText('Blog target').closest('div')).toHaveTextContent('12');
    const published = within(cards).getByText('Published').closest('div')!;
    expect(published).toHaveTextContent('9');
    expect(within(published).getByRole('progressbar', { name: 'Blog target achievement' })).toHaveAttribute('aria-valuenow', '75');
    expect(published).toHaveTextContent('75% of target');
    expect(within(cards).getByText('Remaining').closest('div')).toHaveTextContent('3');
    expect(within(cards).getByText('Leads').closest('div')).toHaveTextContent('45');
    expect(within(cards).getByText('Planned').closest('div')).toHaveTextContent('12');
    expect(within(cards).getByText('In progress')).toBeInTheDocument();

    expect(screen.getByText('Blog Leads target').closest('div')!.parentElement).toHaveTextContent('20of 25');
    expect(within(screen.getByRole('list', { name: 'Published by type' })).getByText('Case study')).toBeInTheDocument();
    const history = screen.getByRole('table', { name: 'Blog history by month' });
    const rows = within(history).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('October 2026');
    expect(rows[1]).toHaveTextContent('75%');
    expect(screen.getByRole('figure', { name: 'Blogs published by month' })).toBeInTheDocument();
  });

  it('leaves targets out for viewers without TARGET_VIEW and offers no editing', async () => {
    mockApi(routes({ 'GET /marketing/content/summary': () => viewerSummary, 'GET /marketing/content/trend': () => ({ targetsVisible: false, months: trend }) }));
    renderPage(<ContentPage />, viewer);

    const cards = await screen.findByRole('region', { name: 'Blog target for October 2026' });
    expect(within(cards).queryByText('Blog target')).not.toBeInTheDocument();
    expect(within(cards).queryByText('Remaining')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table', { name: 'Blog history by month' })).queryByText('Target')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New content' })).not.toBeInTheDocument();
  });

  it('lists content and narrows it to a stage of the pipeline', async () => {
    const user = userEvent.setup();
    const requests: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'GET /marketing/content': (config) => (requests.push(config), page([item({}), draft, oldPost])) }));
    renderPage(<ContentPage />, viewer);

    const row = (await screen.findByRole('button', { name: 'SAP S/4HANA regression testing: a practical guide' })).closest('tr')!;
    expect(within(row).getByText('Published')).toBeInTheDocument();
    expect(within(row).getByLabelText('1 file')).toBeInTheDocument();
    expect(within(screen.getByRole('button', { name: 'Banking QA compliance' }).closest('tr')!).getByText('(month closed)')).toBeInTheDocument();

    const pipeline = screen.getByRole('group', { name: 'Pipeline' });
    const drafts = within(pipeline).getByRole('button', { name: /Draft/ });
    expect(drafts).toHaveTextContent('2');
    await user.click(drafts);
    expect(drafts).toHaveAttribute('aria-pressed', 'true');
    expect(requests.at(-1)?.params).toMatchObject({ status: ['DRAFT'] });

    await user.selectOptions(screen.getByLabelText('List month'), 'PUBLISHED');
    expect(requests.at(-1)?.params).toMatchObject({ month: 10, year: 2026, dateField: 'PUBLISHED' });
  });

  it('moves content through the stages and keeps content with leads published', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/content/1/status': (config) => (puts.push(config), item({ status: 'UPDATED', refreshedDate: '2026-10-09', version: 4 })) }));
    renderPage(<ContentPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA regression testing: a practical guide' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA regression testing/ });
    const group = within(sheet).getByRole('group', { name: 'Change status' });
    expect(within(group).getByRole('button', { name: 'Draft' })).toBeDisabled();
    expect(within(sheet).getByText('Leads name this content, so it stays published.')).toBeInTheDocument();
    await user.click(within(group).getByRole('button', { name: 'Updated' }));
    expect(body(puts[0]!)).toEqual({ version: 3, status: 'UPDATED' });
    expect(await within(sheet).findByText('Refreshed on')).toBeInTheDocument();
  });

  it('lists, uploads, downloads and removes attachments', async () => {
    const user = userEvent.setup();
    const uploads: InternalAxiosRequestConfig[] = [];
    const removed: InternalAxiosRequestConfig[] = [];
    mockApi(
      routes({
        'POST /marketing/content/1/attachments': (config) => (uploads.push(config), [outline, { ...outline, fileId: 42, fileName: 'hero.png', sizeBytes: 1024 }]),
        'DELETE /marketing/content/1/attachments/41': (config) => (removed.push(config), []),
      }),
    );
    renderPage(<ContentPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA regression testing: a practical guide' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA regression testing/ });
    const list = await within(sheet).findByRole('list', { name: 'Attachments' });
    expect(within(list).getByText('outline.docx')).toBeInTheDocument();
    expect(within(list).getByRole('button', { name: 'Download outline.docx' })).toBeInTheDocument();

    fireEvent.change(within(sheet).getByTestId('attachment-input'), { target: { files: [new File(['png'], 'hero.png', { type: 'image/png' })] } });
    expect(await within(sheet).findByText('hero.png')).toBeInTheDocument();
    expect(uploads[0]!.data).toBeInstanceOf(FormData);
    expect((uploads[0]!.data as FormData).get('file')).toBeInstanceOf(File);

    await user.click(within(sheet).getByRole('button', { name: 'Remove outline.docx' }));
    expect(removed).toHaveLength(1);
    expect(await within(sheet).findByText('No files attached.')).toBeInTheDocument();
  });

  it('only shows downloads to viewers', async () => {
    const user = userEvent.setup();
    mockApi(routes());
    renderPage(<ContentPage />, viewer);

    await user.click(await screen.findByRole('button', { name: 'SAP S/4HANA regression testing: a practical guide' }));
    const sheet = await screen.findByRole('dialog', { name: /SAP S\/4HANA regression testing/ });
    await within(sheet).findByText('outline.docx');
    expect(within(sheet).queryByRole('button', { name: 'Remove outline.docx' })).not.toBeInTheDocument();
    expect(within(sheet).queryByRole('button', { name: 'Attach file' })).not.toBeInTheDocument();
    expect(within(sheet).queryByRole('group', { name: 'Change status' })).not.toBeInTheDocument();
  });

  it('creates content and checks the publishing rules first', async () => {
    const user = userEvent.setup();
    const posts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'POST /marketing/content': (config) => (posts.push(config), draft) }));
    renderPage(<ContentPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'New content' }));
    const dialog = await screen.findByRole('dialog', { name: 'New content' });
    expect(within(dialog).getByLabelText(/Published on/)).toBeDisabled();
    await user.type(within(dialog).getByLabelText(/Title/), 'Shift-left testing in agile teams');
    // Publishing dates it today; it then needs a URL.
    await user.selectOptions(within(dialog).getByLabelText(/Status/), 'PUBLISHED');
    expect(within(dialog).getByLabelText(/Published on/)).toHaveValue('2026-10-09');
    await user.click(within(dialog).getByRole('button', { name: 'Add content' }));
    expect(await within(dialog).findByText('Published content needs its URL')).toBeInTheDocument();
    expect(posts).toHaveLength(0);

    await user.type(within(dialog).getByLabelText(/URL/), '/blog/shift-left-testing');
    await user.type(within(dialog).getByLabelText('Organic traffic'), '1,200');
    await user.click(within(dialog).getByRole('button', { name: 'Add content' }));
    expect(body(posts[0]!)).toEqual({
      title: 'Shift-left testing in agile teams',
      url: '/blog/shift-left-testing',
      contentType: 'BLOG',
      status: 'PUBLISHED',
      authorId: 5,
      ownerId: 5,
      plannedDate: null,
      publicationDate: '2026-10-09',
      refreshedDate: null,
      targetPageId: null,
      targetKeywordId: null,
      targetKeywordText: null,
      organicTraffic: 1200,
      ctaClicks: null,
      notes: null,
    });
  }, 15_000);

  it('keeps a post published in a closed month where it counts', async () => {
    const user = userEvent.setup();
    const puts: InternalAxiosRequestConfig[] = [];
    mockApi(routes({ 'PUT /marketing/content/3': (config) => (puts.push(config), oldPost) }));
    renderPage(<ContentPage />, editor);

    await user.click(await screen.findByRole('button', { name: 'Banking QA compliance' }));
    const sheet = await screen.findByRole('dialog', { name: /Banking QA compliance/ });
    expect(within(sheet).getByText('Month closed')).toBeInTheDocument();
    expect(within(within(sheet).getByRole('group', { name: 'Change status' })).getByRole('button', { name: 'Draft' })).toBeDisabled();
    await user.click(within(sheet).getByRole('button', { name: 'Edit content' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edit content' });
    expect(within(dialog).getByLabelText(/Status/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/Type/)).toBeDisabled();
    expect(within(dialog).getByLabelText(/Published on/)).toBeDisabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete content' })).not.toBeInTheDocument();

    await user.clear(within(dialog).getByLabelText('CTA clicks'));
    await user.type(within(dialog).getByLabelText('CTA clicks'), '35');
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    expect(body(puts[0]!)).toMatchObject({ version: 1, status: 'PUBLISHED', contentType: 'BLOG', publicationDate: '2026-06-10', ctaClicks: 35 });
  });
});
