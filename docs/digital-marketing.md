# Digital Marketing

The Digital Marketing module tracks marketing performance against monthly targets: SEO rankings, targets, recurring
activities, email and LinkedIn campaigns, leads, backlinks and content, with an executive dashboard and a monthly
report. It implements brief sections 21–54 and 72–77. Endpoints are listed in [api.md](api.md#digital-marketing) and
tables in [database.md](database.md#digital-marketing).

All formulas live in one place, `com.teamops.marketing.common`: `MarketingMath`, `RankingStatus`, `RankingChange`,
`TargetProgress` and `MarketingPeriod`. Every module, the dashboard and the report use them, so a figure is
calculated the same way everywhere. **Division by zero gives `null`**, which the UI shows as "—".

## Access

- The sidebar section, every `/digital-marketing/*` route and every `/api/marketing/**` endpoint need
  `MARKETING_VIEW`. The API enforces it with a URL rule in `SecurityConfig` plus `@PreAuthorize` on each endpoint.
- Each page also needs its module permission:

| Module | View | Edit |
|---|---|---|
| SEO pages, keywords, rankings | `SEO_VIEW` | `SEO_EDIT` |
| Targets | `TARGET_VIEW` | `TARGET_EDIT` |
| Email and LinkedIn campaigns | `CAMPAIGN_VIEW` | `CAMPAIGN_EDIT` |
| Leads | `LEAD_VIEW` | `LEAD_EDIT` |
| Backlinks | `BACKLINK_VIEW` | `BACKLINK_EDIT` |
| Content & Blog | `CONTENT_VIEW` | `CONTENT_EDIT` |
| Activities, target types, freezing reports | `MARKETING_VIEW` | `MARKETING_EDIT` |

- A Super Admin has every permission. Other people get marketing permissions **per person**, never through a role,
  so only the Digital Marketing team sees the module.
- User administration rejects inconsistent grants: a marketing permission without `MARKETING_VIEW`
  (`MARKETING_VIEW_REQUIRED`), or an edit permission without its view permission (`VIEW_PERMISSION_REQUIRED`).
- The dashboard and the monthly report include a section only when the viewer holds that module's view permission.

## Months and filters

- Everything is monthly. The **business month** comes from `BusinessCalendar` (`APP_TIME_ZONE`, default
  Asia/Kolkata), never from the browser.
- `GET /api/marketing/context` returns today, the default month, selectable years, the people who can own marketing
  work and the target "behind" threshold.
- Month, year and owner live in the URL (`?month=10&year=2026&owner=7`) and are kept when moving between marketing
  pages (`useMarketingFilters()`).
- A month is **open** while it is the current business month or the one before. Closed months are locked for edits
  that would change history; a Super Admin can still correct them, and that correction is flagged in the audit log.

## SEO: pages, keywords and rankings

**Structure:** website page → its keywords → one ranking record per keyword per month.

- **Pages:** URL (unique), title, type (`SERVICE`, `INDUSTRY`, `LOCATION`, `BLOG`, `LANDING_PAGE`, `PRODUCT`,
  `OTHER`), primary keyword, department, owner and status.
- **Keywords:** keyword, page, search engine, location, device, target position, search volume, difficulty, owner
  and status. A keyword is unique per page, engine, location and device.

### Ranking logic (brief sections 26 and 80)

| Position | Colour | Label | Icon |
|---|---|---|---|
| 1–10 | Green | **TOP 10** | trophy |
| 11–100 | Orange | **RANKING** | trending up |
| Not ranked (stored as `NULL`) | Red | **NOT RANKED** | crossed circle |

- **Change = previous − current.** Positive is an improvement (green up arrow, e.g. 12 → 7 is **+5**). Negative is a
  decline (red down arrow). Zero is no change (grey). Going from not ranked to ranked counts as improved, from
  ranked to not ranked as declined, and a keyword's first recorded month is NEW.
- Colour is never the only signal: badges always carry the label, and movement carries an arrow and words for screen
  readers.
- Reference values: position 7 → GREEN, 15 → ORANGE, NR → RED.

### Monthly ranking history (brief sections 25 and 56)

- **Insert-only per month:** `keyword_ranking_history` has a unique key on keyword, month and year. Recording a month
  that already exists answers 409 `RANKING_EXISTS`.
- Each record snapshots the previous month's position and the change at the time it is recorded. Earlier months are
  never overwritten.
- **Corrections:** a month can be corrected while it is open (the current or previous month). The correction changes
  only that month and is audited with before and after values. The next month's snapshot of "previous position"
  follows the correction, but its own position doesn't change.
- Only past and current months can be recorded. Positions must be 1–100 or NR.
- **Monthly update:** `POST /api/marketing/rankings/monthly` records many keywords for one month, all or nothing.
- **Monthly report** (brief section 29): total keywords, top 3, top 10, 11–20, 21–50, 51–100 and not ranked, against
  the month before.
- **Ranking table:** the columns from brief section 27. Sorts: best, worst, biggest improvement, biggest decline.
  Filters: page, keyword, position range, status, movement, owner, month and year.

## Targets (brief sections 30–34)

- Targets are **monthly**, unique per target type and month. History is never overwritten: a month's target can be
  changed only while the month is open (or is a future month), except by a Super Admin. Actuals are never entered for
  future months.
- **Achievement % = actual ÷ target × 100.**
- **Remaining = max(target − actual, 0).**
- **Status:**

| Condition | Status | Colour |
|---|---|---|
| actual ≥ target | **ACHIEVED** | green |
| achievement below the behind threshold | **BEHIND** | red |
| otherwise | **IN PROGRESS** | orange |

- The behind threshold is the admin setting `marketing.target.behindThresholdPct` (default **60%**). A target type can
  override it.
- Reference values: target 250, actual 200 → **80%, 50 remaining, IN PROGRESS**. Target 250, actual 275 → **110%, 0
  remaining, ACHIEVED**.

### Target types and where actuals come from

A type's `actual_source` decides whether its actual is computed or entered by hand. New types can be added from the Targets
page (Target types) by holders of `MARKETING_EDIT`.

| Type | Actual source |
|---|---|
| Website Leads | All leads dated in the month |
| Organic, Blog, Email, LinkedIn, Paid Campaign Leads | Leads of that source dated in the month |
| Backlinks | Backlinks that went live in the month |
| Blogs Published | Blog posts published in the month |
| Landing Pages Created | Landing pages created in the month |
| Keywords in Top 10 | Keywords ranked 1–10 in the month |
| Email Campaigns | Email campaigns sent in the month |
| LinkedIn Campaigns | Paid campaigns running in the month |
| Marketing Prospects (and any `MANUAL` type) | Entered by hand |

The Targets page offers month, quarter and year views and a monthly trend chart per type.

## Recurring marketing activities (brief sections 35–36)

- An activity has a name, description, department, owner, default assignee, frequency, start and optional end date,
  a due offset (days after the period starts), a checklist, and optionally a task title template.
- **Frequencies and periods:**
  - `DAILY`: one day.
  - `WEEKLY`: ISO week, Monday to Sunday.
  - `MONTHLY`: calendar month.
  - `QUARTERLY`: calendar quarter.
  - `YEARLY`: calendar year.
- **Occurrences:** one per activity and period (unique `activity_id + period_start`), with a due date of the period
  start plus the offset, kept inside the period. For example, the October ranking update is due on October 5.
- **Generated tasks:** when the activity has a title template, each occurrence creates a task. The task is assigned
  to the default assignee (or the owner), gets the activity's checklist, and is dated within the period. Placeholders
  in the template are `{month}`, `{quarter}`, `{year}`, `{period}`, `{week}` and `{date}`; for example, "Update
  {month} keyword rankings" becomes "Update October keyword rankings".
- **Completing creates the next occurrence.** Completing an occurrence, or the task it generated, creates the next
  period's occurrence and its task in the same transaction. Skipping (or cancelling the task) does the same.
  Reopening the task reopens the occurrence, and the next occurrence is kept. Past occurrences stay as history.
- No next occurrence is created after the activity's end date or while it is inactive. A period is never created
  twice.
- **Daily job:** `ActivityScheduler` (`MARKETING_ACTIVITY_CRON`, 00:10 in `APP_TIME_ZONE`) makes sure the current
  period's occurrence exists for every active activity. Occurrences without a task get due-soon and overdue
  reminders.

## Email campaigns (brief sections 37–39)

- Zoho campaigns are entered by hand or imported from CSV. `EmailCampaignProvider` is the seam for a later Zoho API
  integration.
- Campaign types: newsletter, lead generation, product promotion, event, recruitment, other.
- **Rates** (all divide by delivered):

| Metric | Formula |
|---|---|
| Open rate | unique opens ÷ delivered × 100 |
| Click rate | unique clicks ÷ delivered × 100 |
| Lead conversion | leads ÷ delivered × 100 |

- Monthly totals add the counts first and then compute the rates, so they are never an average of averages.
- Reference value: 8,500 unique opens ÷ 24,000 delivered = **35.42%**.

## LinkedIn paid campaigns (brief sections 40–42)

- Campaigns (`LINKEDIN` now; `PaidCampaignProvider` is the seam for other platforms) have a budget and **monthly
  results**: spend, impressions, clicks, leads and conversions, one row per campaign and month.
- **Formulas:**

| Metric | Formula |
|---|---|
| CTR | clicks ÷ impressions × 100 |
| CPL | spend ÷ leads |
| Conversion rate | conversions ÷ leads × 100 |
| Remaining budget | budget − spent (negative means overspent and is flagged in words) |

- Reference value: ₹42,000 ÷ 84 leads = **₹500 CPL**.

## Leads (brief sections 43–44)

- **Sources:** organic, email, LinkedIn, paid campaign, blog, website, referral, other.
- **Statuses:** new, contacted, qualified, converted, lost.
- A lead can name the email campaign, paid campaign or content item it came from.
- Leads feed the Website Leads target (every lead in the month) and the per-source lead targets. The leads page shows
  the month against the Website Leads target, with each source against its own target.

## Backlinks (brief sections 45–46)

- **Statuses:** prospected, submitted, approved, live, rejected, lost.
- **Stage rules** (`BacklinkRules`):
  - Each status needs the dates of the stages it went through and allows no later ones.
  - `LIVE` needs submitted and live dates; approval is optional, because some sites publish directly.
  - `LOST` needs submitted, live and lost dates.
  - Stages happen in order and never in the future. A live or lost link needs its URL.
- **Monthly tracking:** each stage counts in the month of its own date.
- **Remaining = max(target − submitted, 0).** The Backlinks target's achievement counts **live** links.
- Reference value: target 50, submitted 35, approved 28, live 22 → **15 remaining**.

## Content & Blog (brief sections 47–48)

- **Statuses:** idea, planned, in progress, draft, published, updated.
- **Publishing rules** (`ContentRules`):
  - Live content (`PUBLISHED`/`UPDATED`) has its URL and a publication date that is never in the future.
  - `UPDATED` content also has a refreshed date, on or after publication.
  - Content that isn't live has neither date, so monthly counts only ever see live content.
- **Monthly tracking:**
  - Live content counts in the month of its publication date.
  - The blog target counts `BLOG` items only.
  - Remaining = max(target − published, 0). Leads are those linked to the content.
- Reference value: target 12, published 9 → **3 remaining**.

## Dashboard and monthly report

- **Dashboard** (`/digital-marketing`, `GET /api/marketing/dashboard`): the month against the month before. It shows a
  KPI row (SEO top 10, leads against target, email open rate, LinkedIn leads and CPL, live backlinks against target,
  blogs against target), followed by:
  - SEO ranking overview
  - target achievement
  - lead source distribution
  - email and LinkedIn performance
  - backlink and content funnels
  - recurring activities
  - a 6/12/24-month trend

  It uses one grouped query per module over the whole trend range, not one per month or row. Rakesh's home dashboard
  shows the same summary.
- **Monthly report** (`/digital-marketing/reports`, `GET /api/marketing/reports/monthly`):
  - Covers SEO, keyword movements, leads, email, LinkedIn, backlinks, content, targets and recurring activities.
  - Each figure is shown against the month before, as "September 2026 → October 2026" with the change and change %
    (rates change by points).
  - Exports as CSV.
  - An ended month can be **frozen** (`MARKETING_EDIT` plus every marketing view permission). After that the report is
    served exactly as it stood, unless `live=true` is requested. Freezing is insert-only and audited.

## CSV import and export (brief section 53)

- **Flow** (`/api/marketing/imports`):
  1. Download the template.
  2. **Preview:** returns valid rows, invalid rows and each row's errors (including duplicates) and saves nothing.
  3. **Commit:** must send the preview's checksum, so only the previewed file is imported. It re-validates in one
     transaction and refuses invalid rows unless `skipInvalid=true`.
- Bad data is never inserted silently. Limits: UTF-8 CSV, 2 MB, 5,000 rows.

| Data | Import | Export |
|---|---|---|
| SEO rankings | `seo-rankings` | ranking table CSV |
| Email campaigns | `email-campaigns` | campaign list CSV |
| Paid campaign results | `paid-campaign-results` | campaign list CSV |
| Leads | `marketing-leads` | lead list CSV |
| Backlinks | `backlinks` | backlink list CSV |
| Content | — | content list CSV |
| Keywords | **not yet** | through the ranking table export |
| Targets | **not yet** | **not yet** (the monthly report export includes target achievement) |

A new import implements `CsvImporter` and registers it as a bean. The frontend reuses `CsvImportDialog`.

## Future integrations (brief sections 52 and 79)

These interfaces have manual implementations today. An integration replaces one by registering a `@Primary` bean,
and `GET /api/marketing/integrations` shows which source each module uses.

| Interface | Later implementations |
|---|---|
| `SeoRankingProvider` | SEMrush, Google Search Console, Ahrefs |
| `EmailCampaignProvider` | Zoho Marketing Automation |
| `PaidCampaignProvider` | LinkedIn Ads, Google Ads, Microsoft Ads |
| `AnalyticsProvider` | Google Analytics |
| `LeadProvider` | Salesforce, Zoho CRM |

## Development data

With `DEV_SEED_ENABLED=true` the seeders create the brief's worked examples relative to today:
- SAP Testing Services ranked 18 → 12 → 7, and S4HANA Testing 25 → 15 → 9.
- Website Leads 250 / 200 this month (organic 80, email 45, LinkedIn 35, blog 20, paid 20).
- Backlinks: 50 target, 35 submitted, 28 approved, 22 live.
- Blogs: 9 of 12 published.
- The SAP S/4HANA LinkedIn campaign: ₹50,000 budget, ₹42,000 spent, 150,000 impressions, 2,800 clicks, 84 leads.
- The SAP Testing Services Outreach email: 25,000 sent, 24,000 delivered, 8,500 opens, 1,250 clicks, 185 leads.
- The recurring activities from brief section 35.

Marketing users are listed in the [README](../README.md#development-credentials).
