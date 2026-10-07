You are a Senior Full-Stack Architect, Senior React/TypeScript Developer, Senior Java Spring Boot Developer, Database Architect, and UI/UX Designer.

I want you to build a production-quality internal company platform called:

"Internal Work & Performance Management System"

The system will combine:

1. Employee Task Management
2. Workload Management
3. Internal Ticketing / Help Desk
4. Project Management
5. Approval Management
6. Team Management
7. Reports & Analytics
8. Company Announcements
9. Knowledge Base
10. Documents
11. Calendar
12. Administration
13. A dedicated Digital Marketing Performance Management module

IMPORTANT:

I am using Claude Pro through VS Code.

Work directly inside the current VS Code workspace.

Before modifying anything:

1. Inspect the complete existing repository.
2. Identify frontend structure.
3. Identify backend structure.
4. Identify database configuration.
5. Identify existing APIs.
6. Identify existing authentication.
7. Identify existing dependencies.
8. Identify what can be reused.
9. Do not unnecessarily delete or rewrite working code.

If the project is already partially implemented, extend the existing architecture instead of creating a second application.

If the project is empty, create the architecture described below.

Do not create hundreds of files blindly.

Build incrementally.

After each major phase:

- Run frontend build.
- Run backend Maven build.
- Run tests where applicable.
- Fix compilation errors.
- Fix TypeScript errors.
- Fix API errors.
- Keep the application runnable.

==================================================
1. BUSINESS PURPOSE
==================================================

The company has approximately 8–10 core team members.

Rakesh is the Reporting Manager and Super Admin.

The main problem is that Rakesh needs a single system to understand:

- Who is working on what?
- How many tasks does each employee have?
- Who is overloaded?
- Who has very little work?
- What is overdue?
- What is due today?
- Which department has the highest workload?
- Which tasks are blocked?
- Which tickets are unresolved?
- Which projects are delayed?
- What approvals are pending?
- What are employees currently working on?
- What has been completed?
- What is planned next?
- How is each department performing?
- How is Digital Marketing performing against monthly targets?

The application must therefore prioritize:

1. Visibility
2. Ownership
3. Workload
4. Deadlines
5. Performance
6. Department-level reporting
7. Digital Marketing metrics
8. Historical tracking
9. Management analytics

==================================================
2. TECHNOLOGY STACK
==================================================

FRONTEND:

- React.js
- TypeScript
- Vite
- React Router
- Tailwind CSS
- shadcn/ui or equivalent high-quality component library
- Lucide React
- Recharts
- React Hook Form
- Zod
- Axios
- TanStack Query where useful

BACKEND:

- Java
- Spring Boot
- Spring Security
- JWT authentication
- Spring Data JPA
- Hibernate
- Bean Validation
- Lombok where appropriate
- REST APIs

DATABASE:

- MySQL 8+

DATABASE MIGRATION:

- Flyway

BUILD:

- Maven

TESTING:

- JUnit
- Mockito
- Spring Boot Test
- Frontend tests where useful

==================================================
3. UI/UX DESIGN
==================================================

I have provided UI screenshots as visual references.

Use them for inspiration only.

Do not copy them directly.

The final application should feel like a modern enterprise SaaS product.

Take inspiration from products such as:

- Linear
- Jira
- Asana
- Monday.com
- Freshdesk
- HubSpot
- Modern analytics dashboards

The UI must be:

- Professional
- Modern
- Attractive
- Clean
- Fast
- Responsive
- Easy to understand
- Enterprise-quality

Use:

- Clean sidebar
- Top navigation
- Cards
- Tables
- Charts
- Filters
- Search
- Status badges
- Priority indicators
- Avatars
- Tooltips
- Drawers
- Modals
- Toast notifications
- Skeleton loaders
- Empty states
- Error states

Avoid:

- Generic Bootstrap appearance
- Excessive gradients
- Excessive animations
- Huge unnecessary cards
- Too many colors
- Cluttered layouts

The interface should make data understandable within seconds.

==================================================
4. USER ROLES
==================================================

Implement proper role-based and department-based access control.

Roles:

SUPER_ADMIN
DEPARTMENT_MANAGER
EMPLOYEE

SUPER_ADMIN:

Rakesh will initially be the Super Admin.

Super Admin can:

- View all departments
- View all employees
- View all tasks
- View all tickets
- View all projects
- View all approvals
- View all reports
- View Digital Marketing
- View all Digital Marketing campaigns
- View SEO rankings
- View monthly targets
- View leads
- View backlinks
- View marketing performance
- Create users
- Disable users
- Create departments
- Configure departments
- Configure workflows
- Configure permissions
- View audit logs

DEPARTMENT_MANAGER:

Can manage their department.

EMPLOYEE:

Can manage/view their own assigned work based on permissions.

IMPORTANT:

Backend authorization must enforce these rules.

Do NOT rely only on frontend route hiding.

==================================================
5. DEPARTMENTS
==================================================

Initial departments:

1. IT
2. Cyber Security
3. HR
4. Talent Acquisition
5. Web Development
6. App Development
7. Digital Marketing
8. Pre-Sales
9. Graphic & Media
10. Payroll

Super Admin must be able to create additional departments.

Each user should belong to a department.

Each department may have:

- Department Manager
- Members
- Department description
- Status
- KPIs
- Workload

==================================================
6. AUTHENTICATION
==================================================

Implement:

Email + Password login.

Use:

Spring Security
JWT
BCrypt password hashing

Routes:

/login

Protected routes require authentication.

Implement:

- Login
- Logout
- Token handling
- Session expiration
- Unauthorized page
- Role-based routes

==================================================
7. MAIN SIDEBAR
==================================================

Use this navigation structure:

Dashboard

MY WORK
  My Tasks
  My Tickets
  My Calendar

WORK MANAGEMENT
  Tasks
  Workload
  Projects

HELP DESK
  Tickets
  SLA

COLLABORATION
  Approvals
  Announcements
  Knowledge Base
  Documents
  Team

DIGITAL MARKETING
  Marketing Dashboard
  SEO Rankings
  Marketing Targets
  Email Campaigns
  Paid Campaigns
  Leads
  Backlinks
  Content & Blog
  Marketing Activities

ANALYTICS
  Reports

ADMINISTRATION
  Users
  Departments
  Settings
  Audit Logs

IMPORTANT:

The DIGITAL MARKETING menu must only appear for:

- SUPER_ADMIN
- Digital Marketing department users with appropriate permissions

Other departments should not see it.

==================================================
8. HOME DASHBOARD
==================================================

Create a highly polished management dashboard.

For Super Admin / Rakesh show:

TOP KPI CARDS:

- Open Tasks
- Due Today
- Overdue Tasks
- Completed This Week
- Open Tickets
- SLA Breaches
- Pending Approvals
- Team Members

Then:

1. Task Status Distribution

Donut chart:

TODO
IN_PROGRESS
BLOCKED
IN_REVIEW
COMPLETED

2. Department Workload

Bar chart.

Example:

IT
Web Development
App Development
Digital Marketing
HR
etc.

3. Employee Workload

Show:

Employee
Department
Active Tasks
Overdue
Workload %

4. Weekly Task Completion

Line/bar chart.

5. Overdue Tasks

6. Upcoming Deadlines

7. Recent Activity

8. Department Performance

9. Digital Marketing Performance Summary

For Rakesh, also show:

SEO average ranking
Keywords in Top 10
Keywords improved
Keywords declined
Monthly marketing target achievement
Email leads
Paid campaign leads
Backlinks created
Blog/content leads

==================================================
9. WORKLOAD MANAGEMENT
==================================================

Create:

/workload

This is one of the most important modules.

Show:

Employee
Department
Total Tasks
Todo
In Progress
Blocked
Completed
Overdue
Due Today
Workload %

Workload levels:

0–40%
LOW

41–70%
NORMAL

71–100%
HIGH

101%+
OVERLOADED

Use visual indicators.

Allow filtering:

- Department
- Employee
- Status
- Priority
- Date range

Allow sorting:

- Highest workload
- Lowest workload
- Most overdue
- Most completed
- Most active

==================================================
10. TASK MANAGEMENT
==================================================

Task fields:

- Task ID
- Title
- Description
- Department
- Project
- Assignee
- Created by
- Priority
- Status
- Start date
- Due date
- Estimated hours
- Actual hours
- Tags
- Attachments
- Checklist
- Dependencies
- Watchers
- Comments
- Created date
- Updated date
- Completed date

Statuses:

TODO
IN_PROGRESS
BLOCKED
IN_REVIEW
COMPLETED
CANCELLED

Priorities:

LOW
MEDIUM
HIGH
URGENT

Features:

- Create
- Edit
- Assign
- Reassign
- Status update
- Priority update
- Comments
- Attachments
- Checklist
- Watchers
- Dependencies
- Tags
- Due dates
- History
- Reopen completed tasks

==================================================
11. TICKET / HELP DESK
==================================================

Implement internal ticketing.

Fields:

- Ticket ID
- Subject
- Description
- Requester
- Department
- Category
- Priority
- Assigned Agent
- Status
- SLA
- Created date
- First response
- Resolution date
- Attachments
- Comments

Statuses:

NEW
OPEN
IN_PROGRESS
WAITING_FOR_REQUESTER
RESOLVED
CLOSED

Generate:

TKT-000001
TKT-000002

etc.

Implement:

Search
Filters
Sorting
Pagination

==================================================
12. SLA MANAGEMENT
==================================================

Allow configurable SLA policies.

Example:

URGENT
First response: 1 hour
Resolution: 4 hours

HIGH
First response: 2 hours
Resolution: 8 hours

MEDIUM
First response: 4 hours
Resolution: 24 hours

LOW
First response: 8 hours
Resolution: 48 hours

Show:

SLA countdown
Warning
Breached
Compliance %

==================================================
13. PROJECT MANAGEMENT
==================================================

Projects contain:

- Project ID
- Name
- Description
- Owner
- Department
- Start date
- End date
- Status
- Progress
- Milestones
- Tasks
- Risks
- Dependencies
- Documents

Statuses:

PLANNING
ACTIVE
ON_HOLD
COMPLETED
CANCELLED

==================================================
14. APPROVAL MANAGEMENT
==================================================

Approval types:

- Access requests
- Software requests
- Purchases
- Expenses
- Marketing creatives
- Recruitment approvals
- Other configurable workflows

Statuses:

PENDING
APPROVED
REJECTED
CANCELLED

==================================================
15. ANNOUNCEMENTS
==================================================

Implement:

- Title
- Description
- Target department
- Priority
- Publish date
- Expiry date
- Acknowledgement required
- Read status
- Acknowledgement status

==================================================
16. KNOWLEDGE BASE
==================================================

Categories:

SOP
HR
IT
Security
Development
Marketing
Operations
FAQ
Troubleshooting

Implement:

Search
Categories
Articles
Tags
Attachments

==================================================
17. DOCUMENT MANAGEMENT
==================================================

Documents:

- Name
- Description
- Department
- Uploaded by
- Version
- File
- Created date
- Updated date

Design storage abstraction so S3/SharePoint/OneDrive integration can be added later.

==================================================
18. TEAM DIRECTORY
==================================================

Show:

- Employee
- Job title
- Department
- Manager
- Email
- Phone
- Location
- Working hours
- Status

Employee profile should show:

- Current tasks
- Completed tasks
- Workload
- Recent activity
- Tickets

==================================================
19. CALENDAR
==================================================

Calendar should display:

- Task deadlines
- Project milestones
- Team events
- Important dates
- Leave
- Approval deadlines

Views:

Month
Week
Agenda

==================================================
20. REPORTS & ANALYTICS
==================================================

Reports:

- Task completion
- Overdue %
- Workload
- Department performance
- Ticket ageing
- SLA compliance
- Employee productivity
- Project progress
- Trends
- Digital Marketing performance

Filters:

- Department
- Employee
- Date range
- Project
- Status

Charts:

- Donut
- Pie
- Bar
- Line
- KPI cards

Allow CSV export.

==================================================
21. DIGITAL MARKETING MODULE
==================================================

THIS IS A MAJOR MODULE.

Create a completely dedicated:

DIGITAL MARKETING

section.

The purpose is to track Digital Marketing performance against monthly targets.

The module should track:

1. SEO keyword rankings
2. Website pages
3. Monthly SEO performance
4. Marketing targets
5. Email campaigns
6. Zoho email campaign metrics
7. LinkedIn paid campaigns
8. Paid campaign spend
9. Paid campaign leads
10. Website leads
11. Prospect targets
12. Blog/content performance
13. Backlinks
14. Monthly backlink progress
15. Recurring marketing activities
16. Marketing tasks
17. Monthly performance trends

==================================================
22. DIGITAL MARKETING DASHBOARD
==================================================

Create:

/digital-marketing

This should be a dedicated executive dashboard.

Top KPI cards:

SEO:

- Total Pages
- Total Keywords
- Top 10 Keywords
- Top 3 Keywords
- Improved Keywords
- Declined Keywords
- Not Ranked Keywords

Lead Generation:

- Monthly Lead Target
- Leads Generated
- Remaining Leads
- Achievement %
- Email Leads
- Paid Campaign Leads
- Organic Leads
- Blog Leads

Campaign:

- Email Campaigns
- Emails Sent
- Open Rate
- Click Rate
- Clicks
- Leads

LinkedIn:

- Campaigns
- Spend
- Impressions
- Clicks
- Leads
- Cost Per Lead

Backlinks:

- Monthly Target
- Submitted
- Approved
- Live
- Remaining

Content:

- Blog Target
- Blogs Published
- Leads Generated
- Remaining

==================================================
23. SEO PAGE MANAGEMENT
==================================================

Create:

SEO Rankings

The Digital Marketing team must be able to enter all important website pages.

Example:

Page:

/services/software-testing

Title:

Software Testing Services

Target keywords:

software testing services
software testing company
software testing services India

Each website page can have multiple keywords.

Database relationship:

Website Page
    ↓
Multiple Keywords
    ↓
Monthly Ranking Records

Create:

Website Pages

Fields:

- Page ID
- Page URL
- Page title
- Page type
- Primary keyword
- Department
- Owner
- Status
- Created date
- Updated date

Page types:

SERVICE
INDUSTRY
LOCATION
BLOG
LANDING_PAGE
PRODUCT
OTHER

==================================================
24. SEO KEYWORD MANAGEMENT
==================================================

Each page can have multiple keywords.

Keyword fields:

- Keyword ID
- Keyword
- Page
- Search engine
- Location
- Device
- Target position
- Current position
- Previous position
- Search volume
- Keyword difficulty
- Owner
- Status

Example:

Keyword:
SAP S/4HANA Testing Services

Page:
SAP Testing Services

Current position:
7

Previous position:
12

Change:
+5 positions

==================================================
25. MONTHLY KEYWORD RANKING TRACKING
==================================================

THIS IS VERY IMPORTANT.

Every keyword must have historical monthly ranking records.

Example:

Keyword:
SAP Testing Services

January:
Position 22

February:
Position 18

March:
Position 14

April:
Position 9

May:
Position 7

June:
Position 5

Do NOT overwrite old rankings.

Every month must create a new ranking record.

Store:

- Keyword
- Page
- Month
- Year
- Ranking position
- Previous ranking
- Change
- Search volume
- Notes

This allows historical SEO analysis.

==================================================
26. SEO RANKING COLOR LOGIC
==================================================

Implement exactly this ranking logic:

POSITION 1–10:

GREEN

Meaning:
Top 10 ranking

POSITION 11–100:

ORANGE

Meaning:
Ranking but outside Top 10

NOT RANKED:

RED

Meaning:
Keyword is not ranking / position unavailable

Also show:

Improved:
GREEN upward indicator

Declined:
RED downward indicator

No change:
GRAY

Example:

Keyword                  Position    Status

SAP Testing              5           GREEN
Software Testing         12          ORANGE
Testing Company          38          ORANGE
Random Keyword           NR          RED

Do not use color alone.

Also show labels/icons for accessibility.

==================================================
27. SEO RANKING TABLE
==================================================

Create a powerful SEO table.

Columns:

Page
Keyword
Current Position
Previous Position
Change
Status
Search Volume
Owner
Last Updated

Filters:

Page
Keyword
Position
Status
Month
Year
Owner

Sort:

Best ranking
Worst ranking
Biggest improvement
Biggest decline

Search keywords.

==================================================
28. SEO PAGE DETAIL
==================================================

When opening a page:

Show:

Page title
URL
Owner
Status
Total keywords
Top 10 keywords
Average position
Improved keywords
Declined keywords
Not ranked keywords

Then:

Keyword table.

Then:

Ranking history chart.

Example:

Jan 22
Feb 18
Mar 14
Apr 9
May 7
Jun 5

Display a line chart.

==================================================
29. SEO MONTHLY REPORT
==================================================

Create monthly SEO summary.

For selected month show:

Total keywords
Top 3
Top 10
11–20
21–50
51–100
Not ranked

Example:

Top 3       18
Top 10      42
11–20       31
21–50       45
51–100      25
Not ranked  12

Also show:

Previous month comparison.

==================================================
30. DIGITAL MARKETING TARGETS
==================================================

Create:

Marketing Targets

This is a CORE module.

Targets must be monthly.

Example:

October 2026

Website Leads Target:
250

Actual:
200

Remaining:
50

Achievement:
80%

Status:
BEHIND TARGET

Formula:

Achievement % =
Actual / Target × 100

Remaining =
Target - Actual

If actual exceeds target:

Remaining = 0

Show:

Target
Actual
Remaining
Achievement %

Use progress bars.

==================================================
31. TARGET STATUS
==================================================

Implement target status.

If:

Actual >= Target:

GREEN
TARGET ACHIEVED

If:

Actual < Target:

ORANGE
TARGET IN PROGRESS

If target is significantly behind:

RED
BEHIND TARGET

But make the threshold configurable.

Example:

October:

Target = 250
Actual = 200

Display:

200 / 250

80%

50 remaining

Progress bar.

==================================================
32. TARGET TYPES
==================================================

Allow configurable target types.

Initial types:

- Website Leads
- Organic Leads
- Blog Leads
- Email Leads
- LinkedIn Leads
- Paid Campaign Leads
- Backlinks
- Blogs Published
- Landing Pages Created
- Keywords in Top 10
- Marketing Prospects
- Email Campaigns
- LinkedIn Campaigns

Super Admin / Marketing Manager can add more target types.

==================================================
33. TARGET MONTHLY HISTORY
==================================================

Never overwrite historical monthly targets.

Example:

January:
Target 200
Actual 185

February:
Target 220
Actual 210

March:
Target 250
Actual 230

October:
Target 250
Actual 200

Display monthly trend chart.

Allow:

Month
Quarter
Year

views.

==================================================
34. TARGET DASHBOARD
==================================================

Create visual cards:

Website Leads

250 Target
200 Achieved
50 Remaining

80%

Backlinks

100 Target
75 Achieved
25 Remaining

75%

Blogs

12 Target
9 Achieved
3 Remaining

75%

Create a target performance table.

==================================================
35. RECURRING MARKETING PROCESSES
==================================================

Create:

Marketing Activities

This should support recurring processes.

Examples:

Monthly SEO keyword ranking update
Monthly backlink submission
Monthly backlink verification
Weekly blog publishing
Monthly website lead report
Monthly competitor analysis
Monthly SEO audit
Monthly email campaign
Monthly LinkedIn campaign
Monthly content calendar
Monthly keyword research
Monthly website page optimization

Each recurring activity should have:

- Activity name
- Description
- Department
- Owner
- Frequency
- Start date
- End date
- Due date
- Status
- Checklist
- Last completed date
- Next due date

Frequencies:

DAILY
WEEKLY
MONTHLY
QUARTERLY
YEARLY

When a recurring process is completed:

Automatically create the next occurrence.

Example:

"Monthly SEO Ranking Update"

October 1
Completed

System creates:

November 1

The previous record must remain in history.

==================================================
36. DIGITAL MARKETING TASKS
==================================================

Marketing activities can generate tasks.

Example:

Recurring process:

"Monthly SEO Ranking Update"

Creates task:

"Update October keyword rankings"

Assigned to:

SEO Executive

Due:

October 5

When completed:

Record completion.

Then automatically create November occurrence.

==================================================
37. ZOHO EMAIL CAMPAIGNS
==================================================

Create:

Email Campaigns

The team currently runs email campaigns using Zoho.

Initially allow manual data entry.

Design the architecture so Zoho API integration can be added later.

Fields:

- Campaign ID
- Campaign name
- Campaign type
- Campaign date
- Owner
- Audience
- Emails sent
- Delivered
- Bounced
- Opened
- Unique opens
- Clicked
- Unique clicks
- Unsubscribed
- Leads generated
- Campaign status
- Notes

Campaign types:

Newsletter
Lead Generation
Product Promotion
Event
Recruitment
Other

==================================================
38. EMAIL CAMPAIGN ANALYTICS
==================================================

Calculate:

Delivery Rate

Open Rate

Click Rate

Click-to-Open Rate

Lead Conversion Rate

Formulas:

Open Rate =
Unique Opens / Delivered × 100

Click Rate =
Unique Clicks / Delivered × 100

Lead Conversion =
Leads / Delivered × 100

Display:

KPI cards
Charts
Campaign table

Monthly email trend.

==================================================
39. ZOHO CAMPAIGN MONTHLY SUMMARY
==================================================

Show:

October:

Campaigns:
8

Emails sent:
25,000

Opened:
8,500

Clicked:
1,250

Leads:
185

Open rate:
34%

Click rate:
5%

Allow month comparison.

==================================================
40. LINKEDIN PAID CAMPAIGNS
==================================================

Create:

Paid Campaigns

Initially support LinkedIn paid campaigns.

Design the architecture so other platforms can be added later.

Platform:

LINKEDIN

Fields:

- Campaign ID
- Campaign name
- Platform
- Campaign objective
- Start date
- End date
- Budget
- Amount spent
- Impressions
- Clicks
- CTR
- Leads
- CPL
- Conversions
- Owner
- Status
- Notes

==================================================
41. LINKEDIN CAMPAIGN ANALYTICS
==================================================

Calculate:

CTR =
Clicks / Impressions × 100

CPL =
Spend / Leads

Conversion rate =
Conversions / Leads × 100

Display:

Spend
Leads
CPL
Impressions
Clicks
CTR

Charts:

Spend vs Leads

Monthly campaign performance.

==================================================
42. PAID CAMPAIGN MONTHLY TRACKING
==================================================

Example:

October LinkedIn Campaign

Budget:
₹50,000

Spent:
₹42,000

Leads:
84

CPL:
₹500

Display:

Budget
Spent
Remaining Budget
Leads
CPL

Use progress indicators.

==================================================
43. LEAD TRACKING
==================================================

Create a basic Marketing Leads module.

Fields:

- Lead ID
- Lead name
- Company
- Email
- Source
- Campaign
- Department
- Date
- Status
- Owner
- Notes

Sources:

Organic
Email
LinkedIn
Paid Campaign
Blog
Website
Referral
Other

Statuses:

NEW
CONTACTED
QUALIFIED
CONVERTED
LOST

==================================================
44. MONTHLY LEAD TARGET TRACKING
==================================================

Connect Leads to Marketing Targets.

Example:

October:

Target:
250

Actual:
200

Sources:

Organic:
80

Email:
45

LinkedIn:
35

Blog:
20

Paid:
20

Total:
200

Remaining:
50

Display this visually.

==================================================
45. BACKLINK MANAGEMENT
==================================================

Create:

Backlinks

Track monthly backlink activity.

Fields:

- Backlink ID
- Target page
- Referring domain
- Link URL
- Anchor text
- Type
- Status
- Submitted date
- Approved date
- Live date
- Owner
- Domain Authority where available
- Notes

Statuses:

PROSPECTED
SUBMITTED
APPROVED
LIVE
REJECTED
LOST

==================================================
46. MONTHLY BACKLINK TRACKING
==================================================

Example:

October Backlink Target:

50

Submitted:

35

Approved:

28

Live:

22

Remaining:

15

Display:

Target
Submitted
Approved
Live
Remaining

Use monthly history.

==================================================
47. BLOG / CONTENT PERFORMANCE
==================================================

Create:

Content & Blog

Track:

- Blog title
- URL
- Author
- Publication date
- Target keyword
- Target page
- Status
- Leads generated
- Organic traffic if manually entered
- CTA clicks
- Owner

Statuses:

IDEA
PLANNED
IN_PROGRESS
DRAFT
PUBLISHED
UPDATED

==================================================
48. MONTHLY BLOG TARGET
==================================================

Example:

October:

Blog target:
12

Published:
9

Remaining:
3

Leads:
45

Display:

12 target
9 achieved
3 remaining

Monthly history.

==================================================
49. DIGITAL MARKETING PERFORMANCE SCORECARD
==================================================

Create an executive scorecard.

Example:

SEO
--------------------------------
Top 10 Keywords       42
Improved Keywords     18
Declined Keywords     7
Not Ranked            12

Lead Generation
--------------------------------
Target                250
Achieved              200
Achievement           80%
Remaining             50

Email
--------------------------------
Sent                  25,000
Opened                8,500
Clicked               1,250
Leads                 185

LinkedIn
--------------------------------
Spend                 ₹42,000
Leads                 84
CPL                   ₹500

Backlinks
--------------------------------
Target                50
Submitted             35
Live                  22

Content
--------------------------------
Target                12
Published             9
Leads                 45

==================================================
50. DIGITAL MARKETING MONTHLY REPORT
==================================================

Create:

/digital-marketing/reports

Allow selecting:

Month
Year

Generate:

SEO summary
Keyword movements
Lead performance
Email campaign performance
Paid campaign performance
Backlink performance
Content performance
Target achievement
Recurring activity completion

Show previous month comparison.

Example:

September → October

Leads:
180 → 200
+11.1%

Top 10 Keywords:
35 → 42
+20%

Backlinks:
18 → 22
+22%

==================================================
51. DIGITAL MARKETING DASHBOARD FILTERS
==================================================

Global filters:

Month
Year
Department
Owner
Campaign
Page
Keyword

When month changes:

All relevant dashboard metrics should update.

==================================================
52. SEO RANKING DATA IMPORT
==================================================

Initially support manual entry.

But architect the application so later we can integrate:

- SEMrush
- Google Search Console
- Ahrefs
- Google Analytics

Do NOT require these integrations now.

Create service interfaces that make future integrations possible.

Example:

SeoRankingProvider

Later:

SemrushRankingProvider
GoogleSearchConsoleProvider

==================================================
53. MARKETING DATA IMPORT/EXPORT
==================================================

Allow CSV import/export for:

Keywords
SEO rankings
Leads
Backlinks
Campaigns
Targets

Important:

Validate imported data.

Show:

Valid records
Invalid records
Errors

Do not silently insert bad data.

==================================================
54. DIGITAL MARKETING PERMISSIONS
==================================================

Create specific permissions.

Examples:

MARKETING_VIEW
MARKETING_EDIT
SEO_VIEW
SEO_EDIT
CAMPAIGN_VIEW
CAMPAIGN_EDIT
TARGET_VIEW
TARGET_EDIT
LEAD_VIEW
LEAD_EDIT
BACKLINK_VIEW
BACKLINK_EDIT
CONTENT_VIEW
CONTENT_EDIT

SUPER_ADMIN has all permissions.

==================================================
55. DATABASE DESIGN
==================================================

Use normalized MySQL tables.

Existing core tables:

users
roles
permissions
user_roles
departments
department_members
tasks
task_comments
task_attachments
task_checklists
task_dependencies
task_watchers
task_history
projects
project_members
project_milestones
tickets
ticket_comments
ticket_attachments
ticket_history
ticket_categories
sla_policies
approvals
approval_steps
announcements
announcement_reads
knowledge_categories
knowledge_articles
documents
document_versions
notifications
calendar_events
audit_logs

Add Digital Marketing tables:

marketing_pages
marketing_keywords
keyword_ranking_history
marketing_targets
marketing_target_types
marketing_activities
marketing_activity_occurrences
email_campaigns
paid_campaigns
marketing_leads
backlinks
content_items
marketing_monthly_reports

Relationships:

marketing_pages
    ↓
marketing_keywords
    ↓
keyword_ranking_history

marketing_targets
    ↓
monthly target records

marketing_activities
    ↓
recurring occurrences

email_campaigns
    ↓
marketing leads

paid_campaigns
    ↓
marketing leads

content_items
    ↓
marketing leads

==================================================
56. SEO DATABASE REQUIREMENTS
==================================================

Never overwrite ranking history.

Use:

keyword_ranking_history

Fields:

id
keyword_id
ranking_month
ranking_year
ranking_position
previous_position
ranking_change
search_volume
notes
created_at

Unique constraint:

keyword_id + ranking_month + ranking_year

This prevents duplicate monthly ranking records.

==================================================
57. TARGET DATABASE REQUIREMENTS
==================================================

marketing_targets:

id
target_type
month
year
target_value
actual_value
owner
department
notes
created_at
updated_at

Unique:

target_type + month + year

Calculate:

remaining
achievement percentage
status

Prefer calculating derived values rather than unnecessarily storing them.

==================================================
58. RECURRING ACTIVITY DATABASE
==================================================

marketing_activities:

id
name
description
frequency
owner
department
start_date
end_date
active
created_at

marketing_activity_occurrences:

id
activity_id
period_start
period_end
due_date
status
completed_at
completed_by
notes

Unique activity + occurrence period.

==================================================
59. API DESIGN
==================================================

Core APIs:

/api/auth

/api/users
/api/departments
/api/tasks
/api/workload
/api/tickets
/api/sla
/api/projects
/api/approvals
/api/announcements
/api/knowledge-base
/api/documents
/api/team
/api/calendar
/api/reports

Digital Marketing APIs:

/api/marketing/dashboard

/api/marketing/pages
/api/marketing/pages/{id}

/api/marketing/keywords
/api/marketing/keywords/{id}

/api/marketing/rankings
/api/marketing/rankings/monthly

/api/marketing/targets
/api/marketing/targets/monthly

/api/marketing/activities
/api/marketing/activities/recurring

/api/marketing/email-campaigns
/api/marketing/email-campaigns/{id}

/api/marketing/paid-campaigns
/api/marketing/paid-campaigns/{id}

/api/marketing/leads

/api/marketing/backlinks

/api/marketing/content

/api/marketing/reports

Use:

DTOs
Services
Repositories
Controllers
Validation
Exception handling
Security

Do not expose JPA entities directly.

==================================================
60. DIGITAL MARKETING FRONTEND STRUCTURE
==================================================

Suggested:

src/features/marketing/

dashboard/
pages/
keywords/
rankings/
targets/
activities/
email-campaigns/
paid-campaigns/
leads/
backlinks/
content/
reports/

Reusable components:

MarketingKpiCard
RankingBadge
RankingTrend
TargetProgress
CampaignCard
LeadSourceChart
MonthlyPerformanceChart
WorkloadChart
MarketingFilterBar
KeywordTable
TargetTable
CampaignTable

==================================================
61. DIGITAL MARKETING DASHBOARD UI
==================================================

Design it as an executive dashboard.

Header:

Digital Marketing
October 2026

Filters:

Month
Year
Owner

KPI row:

SEO
Leads
Email
LinkedIn
Backlinks
Content

Then:

SEO Ranking Overview

Target Achievement

Lead Source Distribution

Email Campaign Performance

LinkedIn Campaign Performance

Backlink Progress

Content Performance

Recurring Activities

Monthly Trend

==================================================
62. VISUAL STATUS SYSTEM
==================================================

Use consistent semantic status colors.

GREEN:

Healthy
Completed
Achieved
Top 10
Live
Approved

ORANGE:

Warning
In Progress
11–100 ranking
Behind target
Pending

RED:

Critical
Overdue
Not ranked
Breached
Rejected

GRAY:

Inactive
Not started
No data

Do not use color alone.

Use labels/icons as well.

==================================================
63. AUDIT LOG
==================================================

Track:

User login
Task creation
Task assignment
Task status change
Ticket creation
Ticket status change
Target modification
SEO ranking modification
Campaign creation
Campaign modification
Lead modification
Backlink modification
User permission change

==================================================
64. FILE UPLOAD
==================================================

Support attachments for:

Tasks
Tickets
Projects
Documents
Marketing content

Validate:

File type
File size
Filename

Initially local storage.

Keep abstraction for future S3 integration.

==================================================
65. NOTIFICATIONS
==================================================

Notify users when:

Task assigned
Task overdue
Task due soon
Ticket assigned
Approval required
Approval completed
Announcement published
Mentioned in comment

Digital Marketing notifications:

Monthly SEO ranking due
Monthly target review due
Backlink target due
Blog target due
Email campaign scheduled
Recurring marketing activity due

==================================================
66. GLOBAL SEARCH
==================================================

Search across:

Tasks
Tickets
Projects
Employees
Knowledge Base
Marketing Pages
Keywords
Campaigns
Leads

==================================================
67. EMPLOYEE DASHBOARD
==================================================

Employee dashboard:

My Tasks
Due Today
Overdue
In Progress
Completed
My Tickets
Upcoming Deadlines
Notifications

Digital Marketing employee:

Also show:

SEO tasks
Marketing activities
Campaign tasks
Monthly targets assigned to them

==================================================
68. DEPARTMENT DASHBOARD
==================================================

Department Manager dashboard:

Team workload
Department tasks
Department tickets
Projects
Performance
Overdue work

Digital Marketing manager additionally sees:

SEO
Leads
Campaigns
Backlinks
Content
Targets

==================================================
69. SECURITY
==================================================

Implement:

JWT
BCrypt
Role authorization
Permission authorization
Department authorization
Input validation
CORS
Secure file upload
Global exception handling

Never trust authorization information from frontend.

Backend must determine:

Current user
Current role
Current department
Permissions

==================================================
70. PERFORMANCE
==================================================

Frontend:

- Lazy loading
- Code splitting
- Pagination
- Debounced search
- Query caching
- Avoid unnecessary re-renders

Backend:

- Pagination
- Proper indexes
- Efficient queries
- Avoid N+1
- DTO projections where useful
- Aggregation queries for dashboard

Digital Marketing dashboard should not execute dozens of unnecessary database queries.

Prefer optimized aggregate queries.

==================================================
71. SEED DATA
==================================================

Create development seed data.

Super Admin:

Rakesh
Role:
SUPER_ADMIN

Create sample users across:

IT
Cyber Security
HR
Talent Acquisition
Web Development
App Development
Digital Marketing
Pre-Sales
Graphic & Media
Payroll

Create sample:

Tasks
Tickets
Projects
SEO pages
SEO keywords
Monthly rankings
Marketing targets
Email campaigns
LinkedIn campaigns
Leads
Backlinks
Blogs
Recurring activities

Make the dashboard visually populated during development.

Do not use production credentials.

Clearly document development credentials.

==================================================
72. SAMPLE SEO DATA
==================================================

Create example:

Page:

/services/sap-testing

Keywords:

SAP Testing Services
SAP S4HANA Testing
SAP Testing Company
SAP Application Testing

Example monthly history:

SAP Testing Services

August: 18
September: 12
October: 7

S4HANA Testing

August: 25
September: 15
October: 9

Create charts using this history.

==================================================
73. SAMPLE MARKETING TARGET
==================================================

October 2026:

Website Lead Target:
250

Actual:
200

Remaining:
50

Achievement:
80%

Show:

TARGET ACHIEVEMENT

200 / 250

80%

50 remaining

==================================================
74. SAMPLE BACKLINK TARGET
==================================================

October 2026:

Target:
50

Submitted:
35

Approved:
28

Live:
22

Remaining:
15

==================================================
75. SAMPLE BLOG TARGET
==================================================

October 2026:

Target:
12

Published:
9

Remaining:
3

Leads:
45

==================================================
76. SAMPLE LINKEDIN CAMPAIGN
==================================================

October 2026:

Campaign:
SAP S/4HANA Testing Campaign

Budget:
₹50,000

Spent:
₹42,000

Impressions:
150,000

Clicks:
2,800

Leads:
84

Calculate:

CTR
CPL

==================================================
77. SAMPLE EMAIL CAMPAIGN
==================================================

October 2026:

Campaign:

SAP Testing Services Outreach

Emails Sent:
25,000

Delivered:
24,000

Opened:
8,500

Clicked:
1,250

Leads:
185

Calculate:

Open Rate
Click Rate
Lead Conversion Rate

==================================================
78. REPORTING
==================================================

Create management reports.

Overall:

- Department workload
- Employee workload
- Task completion
- Ticket performance
- Project progress

Digital Marketing:

- SEO monthly report
- Keyword ranking report
- Target achievement report
- Lead generation report
- Email campaign report
- Paid campaign report
- Backlink report
- Content report

Allow:

CSV export.

Design architecture for future PDF reporting.

==================================================
79. FUTURE INTEGRATIONS
==================================================

Do not implement now unless already available.

But architect for:

Zoho Marketing Automation
Google Search Console
Google Analytics
SEMrush
Ahrefs
LinkedIn Ads
Microsoft Ads
Google Ads
Salesforce
Zoho CRM

Use provider/service abstraction where appropriate.

For example:

EmailCampaignProvider
SeoRankingProvider
AnalyticsProvider
LeadProvider

Later implementations can be:

ZohoEmailCampaignProvider
SemrushSeoProvider
GoogleAnalyticsProvider

==================================================
80. IMPORTANT DIGITAL MARKETING BUSINESS RULES
==================================================

SEO:

Position 1–10:
GREEN / TOP 10

Position 11–100:
ORANGE / RANKING

Not ranked:
RED / NOT RANKED

Targets:

Actual >= Target:
ACHIEVED

Actual < Target:
IN PROGRESS / BEHIND depending on threshold

Remaining:

max(Target - Actual, 0)

Achievement:

Actual / Target × 100

Email:

Open Rate =
Unique Opens / Delivered × 100

Click Rate =
Unique Clicks / Delivered × 100

LinkedIn:

CTR =
Clicks / Impressions × 100

CPL =
Spend / Leads

Backlinks:

Track separately:

Target
Submitted
Approved
Live
Remaining

Content:

Target
Published
Remaining
Leads

==================================================
81. MOST IMPORTANT MANAGEMENT VIEW
==================================================

Rakesh should be able to open the application and understand within 30 seconds:

TEAM:

Who is overloaded?

Who has overdue work?

Who is completing work?

Which department is overloaded?

TASKS:

What needs attention today?

MARKETING:

Are we achieving monthly targets?

SEO:

Are rankings improving?

LEADS:

Are we generating enough leads?

EMAIL:

How many emails were sent?

How many opened?

How many clicked?

How many leads generated?

PAID CAMPAIGNS:

How much did we spend?

How many leads generated?

What is CPL?

BACKLINKS:

How many submitted?

How many live?

What is remaining?

CONTENT:

How many blogs planned?

How many published?

How many leads generated?

==================================================
82. FINAL DASHBOARD CONCEPT
==================================================

Super Admin dashboard:

------------------------------------------------------------
Good Morning, Rakesh

October 2026
------------------------------------------------------------

TEAM KPIs

Open Tasks | Due Today | Overdue | Tickets | Approvals

------------------------------------------------------------

TEAM WORKLOAD
Employee workload chart

DEPARTMENT WORKLOAD
Department bar chart

------------------------------------------------------------

TASK STATUS
Donut chart

OVERDUE TASKS
Table

------------------------------------------------------------

DIGITAL MARKETING

SEO:
Top 10 Keywords
Improved
Declined
Not Ranked

LEADS:
200 / 250
80%
50 Remaining

EMAIL:
25K Sent
8.5K Opened
1.25K Clicked
185 Leads

LINKEDIN:
₹42K Spend
84 Leads
₹500 CPL

BACKLINKS:
22 Live / 50 Target

CONTENT:
9 / 12 Blogs

------------------------------------------------------------

RECENT ACTIVITY

UPCOMING DEADLINES

==================================================
83. DEVELOPMENT PROCESS
==================================================

PHASE 1:

Inspect repository.

Report:

- Existing frontend
- Existing backend
- Existing DB
- Dependencies
- Authentication
- APIs
- Existing components

PHASE 2:

Architecture and database design.

PHASE 3:

Authentication.

PHASE 4:

Users + departments + roles.

PHASE 5:

Tasks + workload.

PHASE 6:

Dashboard.

PHASE 7:

Tickets + SLA.

PHASE 8:

Projects + approvals.

PHASE 9:

Digital Marketing foundation.

PHASE 10:

SEO pages + keywords.

PHASE 11:

Monthly ranking history.

PHASE 12:

Marketing targets.

PHASE 13:

Recurring marketing activities.

PHASE 14:

Email campaigns.

PHASE 15:

LinkedIn paid campaigns.

PHASE 16:

Marketing leads.

PHASE 17:

Backlinks.

PHASE 18:

Blog/content.

PHASE 19:

Digital Marketing dashboard.

PHASE 20:

Reports.

PHASE 21:

Admin + audit.

PHASE 22:

UI polish.

PHASE 23:

Testing.

PHASE 24:

Performance optimization.

==================================================
84. CODING RULES
==================================================

Before creating a new component/service/file:

Check whether an existing equivalent exists.

Do not duplicate.

Do not use "any" unnecessarily.

Use proper TypeScript types.

Use DTOs.

Keep controllers thin.

Put business logic inside services.

Use repositories for persistence.

Use validation.

Use centralized exception handling.

Use environment variables.

Create:

.env.example

Never commit secrets.

==================================================
85. DOCUMENTATION
==================================================

Create:

README.md

docs/architecture.md
docs/database.md
docs/api.md
docs/digital-marketing.md

Document:

Installation
Architecture
Database
Authentication
Authorization
API
Digital Marketing
SEO ranking logic
Target calculations
Recurring activities
Development credentials
Environment variables
Deployment

==================================================
86. TESTING
==================================================

Test:

Authentication
Authorization
Task creation
Task assignment
Workload calculation
Ticket creation
SLA calculation
SEO ranking status
Monthly ranking history
Target calculation
Target achievement
Recurring activity generation
Email metrics
Paid campaign metrics
Backlink metrics
Lead calculations

Examples:

Position 7 -> GREEN

Position 15 -> ORANGE

Not ranked -> RED

Target 250 / Actual 200 -> 80%, remaining 50

Target 250 / Actual 275 -> 110%, remaining 0

Email:

Delivered 24,000
Opened 8,500

Open Rate =
35.42%

LinkedIn:

Spend ₹42,000
Leads 84

CPL =
₹500

==================================================
87. FINAL QUALITY STANDARD
==================================================

The final application must NOT look like:

- College project
- Basic CRUD application
- Generic admin template
- Plain HTML forms
- Default Bootstrap dashboard

It must look like a real commercial enterprise SaaS application.

The UI should be attractive enough that employees actually want to use it.

The Digital Marketing module should feel like a lightweight internal combination of:

SEO tracker
Marketing dashboard
Campaign tracker
Lead tracker
Target tracker
Backlink tracker
Content tracker

but integrated into the company's main task management system.

==================================================
88. START NOW
==================================================

Start by inspecting the current VS Code workspace.

DO NOT immediately generate hundreds of files.

First:

1. Inspect repository.
2. Understand current architecture.
3. Identify reusable code.
4. Identify missing components.
5. Design database changes.
6. Give a concise implementation plan.
7. Start Phase 1.
8. Keep the project runnable.
9. Build incrementally.
10. Run builds/tests after each major phase.

Do not ask unnecessary questions.

Where requirements are clear, make reasonable engineering decisions and continue.

The most important goal is:

BUILD A REAL, WORKING, BEAUTIFUL INTERNAL MANAGEMENT SYSTEM THAT GIVES RAKESH COMPLETE VISIBILITY INTO TEAM WORKLOAD AND DIGITAL MARKETING PERFORMANCE.