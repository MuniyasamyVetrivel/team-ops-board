import type { MarketingDashboard } from '@/features/marketing/dashboard/api';
import type { SeoStats } from '@/features/marketing/seo/api';
import type { TargetItem, TypeRef } from '@/features/marketing/targets/api';

/** Marketing dashboard fixtures: brief section 49's October 2026 scorecard. */
export const priya = { id: 11, fullName: 'Priya Menon', email: 'priya.menon@teamops.local', jobTitle: 'Digital Marketing Manager', status: 'ACTIVE' as const };
export const arun = { id: 12, fullName: 'Arun Kumar', email: 'arun.kumar@teamops.local', jobTitle: 'SEO Executive', status: 'ACTIVE' as const };
const dm = { id: 7, name: 'Digital Marketing', code: 'DM' };

const stats = (s: Partial<SeoStats>): SeoStats => ({
  totalKeywords: 66,
  top3: 15,
  top10: 42,
  ranking: 12,
  positions11to20: 6,
  positions21to50: 4,
  positions51to100: 2,
  notRanked: 12,
  notRecorded: 0,
  improved: 18,
  declined: 7,
  unchanged: 29,
  averagePosition: 9.4,
  ...s,
});

const ref = (id: number, name: string, actualSource: TypeRef['actualSource']): TypeRef => ({ id, code: name.toUpperCase().replace(/ /g, '_'), name, unit: 'COUNT', actualSource, automatic: true });

function target(type: TypeRef, value: number, actual: number, pct: number, remaining: number, status: TargetItem['status']): TargetItem {
  return {
    id: type.id,
    type,
    month: 10,
    year: 2026,
    label: 'October 2026',
    targetValue: value,
    actual,
    actualOrigin: 'AUTOMATIC',
    achievementPct: pct,
    remaining,
    status,
    thresholdPct: 60,
    owner: priya,
    department: dm,
    notes: null,
    editable: false,
    actualEditable: false,
    version: 0,
    createdAt: '2026-10-01T05:00:00Z',
    updatedAt: '2026-10-05T05:00:00Z',
  };
}

/** Brief section 49 scorecard for October 2026. */
const websiteLeads = target(ref(1, 'Website Leads', 'LEADS'), 250, 200, 80, 50, 'IN_PROGRESS');
const backlinkTarget = target(ref(7, 'Backlinks', 'BACKLINKS_LIVE'), 50, 22, 44, 28, 'BEHIND');
const blogTarget = target(ref(8, 'Blogs Published', 'BLOGS_PUBLISHED'), 12, 9, 75, 3, 'IN_PROGRESS');

const october = { month: 10, year: 2026, label: 'October 2026' };
const september = { month: 9, year: 2026, label: 'September 2026' };

export const marketingDashboard: MarketingDashboard = {
  period: october,
  comparisonPeriod: september,
  today: '2026-10-09',
  targetsVisible: true,
  seo: { totalPages: 6, current: stats({}), comparison: stats({ top10: 35, notRanked: 15, averagePosition: 11.2 }) },
  leads: {
    total: 200,
    comparisonTotal: 180,
    bySource: [
      { source: 'ORGANIC', leads: 80, comparison: 70 },
      { source: 'EMAIL', leads: 45, comparison: 50 },
      { source: 'LINKEDIN', leads: 35, comparison: 35 },
      { source: 'PAID_CAMPAIGN', leads: 20, comparison: 15 },
      { source: 'BLOG', leads: 20, comparison: 10 },
      { source: 'WEBSITE', leads: 0, comparison: 0 },
      { source: 'REFERRAL', leads: 0, comparison: 0 },
      { source: 'OTHER', leads: 0, comparison: 0 },
    ],
    target: websiteLeads,
  },
  email: {
    current: {
      campaigns: 1,
      counts: { emailsSent: 25_000, delivered: 24_000, bounced: 1_000, opened: 10_000, uniqueOpens: 8_500, clicked: 2_000, uniqueClicks: 1_250, unsubscribed: 40, leads: 185 },
      rates: { deliveryRate: 96, openRate: 35.42, clickRate: 5.21, clickToOpenRate: 14.71, leadConversionRate: 0.77, bounceRate: 4, unsubscribeRate: 0.17 },
    },
    comparison: {
      campaigns: 1,
      counts: { emailsSent: 20_000, delivered: 19_000, bounced: 1_000, opened: 7_000, uniqueOpens: 6_000, clicked: 1_500, uniqueClicks: 900, unsubscribed: 30, leads: 120 },
      rates: { deliveryRate: 95, openRate: 31.58, clickRate: 4.74, clickToOpenRate: 15, leadConversionRate: 0.63, bounceRate: 5, unsubscribeRate: 0.16 },
    },
  },
  linkedin: {
    current: { campaigns: 1, results: { spend: 42_000, impressions: 150_000, clicks: 2_800, leads: 84, conversions: 21 }, rates: { ctr: 1.87, costPerLead: 500, conversionRate: 25, costPerClick: 15 } },
    comparison: { campaigns: 1, results: { spend: 48_000, impressions: 200_000, clicks: 2_400, leads: 60, conversions: 18 }, rates: { ctr: 1.2, costPerLead: 800, conversionRate: 30, costPerClick: 20 } },
    runningCampaigns: 1,
    budget: { budget: 50_000, spent: 42_000, remaining: 8_000, usedPct: 84, overBudget: false },
  },
  backlinks: {
    current: { period: october, submitted: 35, approved: 28, live: 22, rejected: 2, lost: 1 },
    comparison: { period: september, submitted: 46, approved: 43, live: 41, rejected: 3, lost: 0 },
    target: { targetValue: 50, remaining: 15, target: backlinkTarget },
  },
  content: {
    current: { period: october, plannedBlogs: 12, publishedBlogs: 9, publishedAll: 10, refreshed: 1, leads: 45 },
    comparison: { period: september, plannedBlogs: 11, publishedBlogs: 11, publishedAll: 12, refreshed: 0, leads: 40 },
    blogTarget: { targetValue: 12, remaining: 3, target: blogTarget },
  },
  targets: { targets: [websiteLeads, backlinkTarget, blogTarget], summary: { total: 3, achieved: 0, inProgress: 2, behind: 1 } },
  activities: {
    due: 10,
    completed: 6,
    skipped: 2,
    open: 2,
    overdue: 1,
    completionPct: 75,
    attention: [
      {
        id: 31,
        activityId: 3,
        activityName: 'Monthly SEO ranking update',
        frequency: 'MONTHLY',
        periodStart: '2026-10-01',
        periodEnd: '2026-10-31',
        periodLabel: 'October 2026',
        dueDate: '2026-10-05',
        dueState: 'OVERDUE',
        status: 'PENDING',
        task: null,
        assignee: arun,
        completedAt: null,
        completedBy: null,
        notes: null,
        version: 0,
        canAct: false,
      },
    ],
  },
  trend: [
    { period: september, leads: 180, top10Keywords: 35, emailLeads: 50, linkedinSpend: 48_000, linkedinLeads: 60, backlinksLive: 41, blogsPublished: 11 },
    { period: october, leads: 200, top10Keywords: 42, emailLeads: 45, linkedinSpend: 42_000, linkedinLeads: 84, backlinksLive: 22, blogsPublished: 9 },
  ],
};

/** Marketing access only: activities and an empty trend. */
export const bareMarketingDashboard: MarketingDashboard = {
  ...marketingDashboard,
  targetsVisible: false,
  seo: null,
  leads: null,
  email: null,
  linkedin: null,
  backlinks: null,
  content: null,
  targets: null,
  activities: { ...marketingDashboard.activities, attention: [] },
  trend: marketingDashboard.trend.map((t) => ({ period: t.period, leads: null, top10Keywords: null, emailLeads: null, linkedinSpend: null, linkedinLeads: null, backlinksLive: null, blogsPublished: null })),
};

