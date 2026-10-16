# API Reference

Base path: `/api`. JSON in and out. Every endpoint except the public auth endpoints requires `Authorization: Bearer <accessToken>`.

The **Permission** column is the `@PreAuthorize` rule; services then apply data scope (Super Admin: everything; manager: their departments; everyone else: their own work) and return **404** for records outside it. Dates are ISO (`2026-10-10`), timestamps UTC ISO-8601. Business "today", due states and SLA states are computed on the server in `APP_TIME_ZONE`. CSV downloads are served as attachments with `X-Content-Type-Options: nosniff`.

**Contents:** [Errors](#errors) · [Authentication](#authentication-apiauth) · [Paging](#lists-and-paging) · [Users](#users-apiusers) · [Departments](#departments-apidepartments) · [Team](#team-directory-apiteam) · [Tasks](#tasks-apitasks) · [Workload](#workload-apiworkload) · [Dashboard](#dashboard-apidashboard) · [Notifications](#notifications-apinotifications) · [Calendar](#calendar-apicalendar) · [Tickets](#tickets-apitickets) · [SLA](#sla-apisla) · [Projects](#projects-apiprojects) · [Approvals](#approvals-apiapprovals) · [Announcements](#announcements-apiannouncements) · [Knowledge base](#knowledge-base-apiknowledge-base) · [Documents](#documents-apidocuments) · [Reports](#reports-apireports) · [Search](#global-search-apisearch) · [Administration](#administration) · [Digital Marketing](#digital-marketing)

## Errors

Every error uses the same body (`com.teamops.common.exception.ApiError`):

```json
{
  "timestamp": "2026-10-07T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/login",
  "fieldErrors": [{ "field": "email", "message": "Enter a valid email address" }]
}
```

| Status | Typical `code` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `INVALID_SORT`, `WEAK_PASSWORD` | Invalid input |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `SESSION_EXPIRED` | Missing, invalid or expired credentials. The client should refresh or sign in |
| 403 | `FORBIDDEN`, `ACCOUNT_DISABLED` | Authenticated but not allowed |
| 404 | `NOT_FOUND` | Unknown resource |
| 409 | e.g. `EMAIL_IN_USE`, `STALE_UPDATE` | Conflict with existing data, or the record changed since you read it (send the latest `version`) |
| 500 | `INTERNAL_ERROR` | Unexpected error. Details are logged server-side, never returned |

## Authentication: `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Email + password. Returns an access token and sets the refresh cookie |
| POST | `/api/auth/refresh` | refresh cookie | Rotates the refresh cookie and returns a new access token |
| POST | `/api/auth/logout` | refresh cookie (optional) | Revokes the refresh token and clears the cookie. Always `204` |
| GET | `/api/auth/me` | bearer | Current user, department, roles, effective permissions and `mustChangePassword` |
| POST | `/api/auth/change-password` | bearer | `{ "currentPassword", "newPassword" }`. Clears `mustChangePassword`, signs out every other session, and returns a new session (like login). Errors: `WRONG_PASSWORD`, `WEAK_PASSWORD`, `PASSWORD_UNCHANGED` |

When an administrator chose the password (new user, CSV import, bootstrap or reset), `mustChangePassword` is `true`.
Until the user changes it, every other endpoint answers `403 PASSWORD_CHANGE_REQUIRED`. Passwords need at least 10
characters, with letters and numbers.

Any bearer header sent to `login`, `refresh` or `logout` is ignored, so a stale access token never blocks them.

### POST /api/auth/login

```json
{ "email": "rakesh@teamops.local", "password": "…" }
```

**200**, with header `Set-Cookie: tob_refresh=…; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=604800`:

```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-10-07T11:00:00Z",
  "user": {
    "id": 1,
    "email": "rakesh@teamops.local",
    "firstName": "Rakesh",
    "lastName": "",
    "fullName": "Rakesh",
    "jobTitle": "Reporting Manager",
    "department": { "id": 1, "name": "IT", "code": "IT" },
    "roles": ["SUPER_ADMIN"],
    "permissions": ["APPROVAL_CONFIGURE", "…"]
  }
}
```

Errors:
- **401 `INVALID_CREDENTIALS`** is returned for both an unknown email and a wrong password, with the same message and similar timing.
- **403 `ACCOUNT_DISABLED`** is returned only when the password is correct.

### POST /api/auth/refresh

Reads the `tob_refresh` cookie. Returns the same body as login and sets a new cookie; the old one is revoked.
- **401 `SESSION_EXPIRED`** if the cookie is missing, unknown, expired or already used.
- Re-using an already-rotated token (outside a 10-second grace window for concurrent tabs) revokes **all** sessions of that user and is audited as `REFRESH_TOKEN_REUSE`.

### GET /api/auth/me

Returns the `user` object shown above. Roles and permissions are computed by the backend; SUPER_ADMIN always receives the full permission catalogue.

## Lists and paging

List endpoints return `{ content, page, size, totalElements, totalPages }` and accept these parameters:
- `page`: zero-based.
- `size`: capped at 100.
- `sort=field,asc|desc`: only the fields listed for each endpoint are accepted. Anything else returns 400 `INVALID_SORT`.

## Users: `/api/users`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/users` | `USER_MANAGE` | Search. Filters: `search` (name, email or job title), `departmentId`, `status` (`ACTIVE`/`DISABLED`), `role`. Sort fields: `name`, `email`, `department`, `lastLogin`, `created` |
| GET | `/api/users/{id}` | `USER_MANAGE` | Full record, including roles, direct grants and effective permissions |
| POST | `/api/users` | `USER_MANAGE` | Create. The body includes `password` (10+ characters with letters and numbers; the user must change it at first sign-in), `roles` and `permissions`. Returns `201` |
| PUT | `/api/users/{id}` | `USER_MANAGE` | Edit profile fields: email, names, job title, contact details, `timeZone` (IANA, e.g. `Asia/Kolkata`), department, `reportsToId`, `weeklyCapacityHours` |
| PUT | `/api/users/{id}/access` | `PERMISSION_MANAGE` | Replace `roles` and direct `permissions`. Audited with before and after values |
| POST | `/api/users/{id}/disable` / `enable` | `USER_MANAGE` | Disabling revokes every session immediately |
| POST | `/api/users/{id}/reset-password` | `USER_MANAGE` | `{ "newPassword": "…" }`. Signs the user out everywhere; they must change it at their next sign-in. Returns `204` |
| GET | `/api/users/import` | `USER_MANAGE` | The users CSV import definition (columns) |
| GET | `/api/users/import/template` | `USER_MANAGE` | Header and example row, as a download |
| POST | `/api/users/import/preview` | `USER_MANAGE` | Multipart `file`. Validates every row and returns valid rows, invalid rows and errors. Saves nothing |
| POST | `/api/users/import/commit` | `USER_MANAGE` | Multipart `file` + `checksum` (from the preview) + `skipInvalid`. Creates the users, each with a random temporary password, and returns them once in `resultFile` (`Cache-Control: no-store`). Audited as `USERS_IMPORTED` and `CSV_IMPORTED` |
| GET | `/api/roles`, `/api/permissions` | `USER_MANAGE` or `PERMISSION_MANAGE` | Role and permission catalogue |

These rules are enforced by the service, so they apply even to someone who holds the permission:

| Code | Status | Rule |
|---|---|---|
| `CANNOT_GRANT_PERMISSIONS` | 403 | You can only grant permissions, including those that come with a role, that you hold yourself |
| `CANNOT_GRANT_SUPER_ADMIN` | 403 | Only a Super Admin can grant the Super Admin role |
| `SUPER_ADMIN_PROTECTED` | 403 | Only a Super Admin can disable, demote or reset the password of a Super Admin |
| `CANNOT_MODIFY_SELF` | 403 | You cannot disable yourself, change your own access, or reset your own password here |
| `LAST_SUPER_ADMIN` | 409 | At least one active Super Admin must remain |
| `EMAIL_IN_USE` | 409 | Emails are unique and case-insensitive |
| `INVALID_REPORTS_TO` | 400 | A user cannot report to themselves, and reporting lines cannot form a loop |
| `DEPARTMENT_INACTIVE` | 400 | Users cannot be placed in an inactive department |

## Departments: `/api/departments`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/departments` | any signed-in user | All departments, with manager, `memberCount` (active primary members) and `secondaryMemberCount` |
| GET | `/api/departments/{id}` | `TEAM_VIEW` or `DEPARTMENT_MANAGE` | Detail with primary and secondary members, plus `canEdit` and `canManageMembers` for this viewer |
| POST | `/api/departments` | `DEPARTMENT_MANAGE` | `{ name, code, description?, managerId? }`. The code is 2–40 characters (`A–Z`, `0–9`, `_`), stored in upper case and can't be changed later |
| PUT | `/api/departments/{id}` | `DEPARTMENT_MANAGE` | `{ name, description?, managerId?, status }`. Returns 409 `DEPARTMENT_HAS_ACTIVE_USERS` when deactivating a department that still has active users |
| PUT | `/api/departments/{id}/members/{userId}` | `DEPARTMENT_MANAGE`, or a manager of that department | `{ "role": "MEMBER" \| "MANAGER" }` adds or updates a secondary membership. Only `DEPARTMENT_MANAGE` can grant `MANAGER` |
| DELETE | `/api/departments/{id}/members/{userId}` | same as above | Removes a secondary membership |

## Team directory: `/api/team`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/team` | `TEAM_VIEW` | Directory. Filters: `search`, `departmentId`, `status` (default `ACTIVE`). Sort fields: `name`, `department`, `jobTitle` |
| GET | `/api/team/{id}` | `TEAM_VIEW` | Profile with roles, manager and direct reports. `canViewWork` says whether the viewer may see this person's tasks, workload and tickets (Super Admin, their department manager, or the person themselves) |
| GET | `/api/team/{id}/tasks` | `TEAM_VIEW` + `TASK_VIEW` | Work history: tasks assigned to the person, paged. Rows: code, title, project, priority, status, start, due and completed dates, estimated and actual hours, `overdue`, `onTime` (completed on or before the due date) and `daysLate`. Sort: `due`, `completed`, `priority` (urgent first when `desc`), `updated` (default `updated,desc`) |
| GET | `/api/team/{id}/tasks/summary` | `TEAM_VIEW` + `TASK_VIEW` | Same filters: `total`, `byStatus`, `overdue`, `completed`, `completedOnTimePct`, `averageDaysToComplete` (from start, else creation), `totalActualHours`, `completedPerWeek` (weeks of the range, or the last 12), plus `today`, `from` and `to` |
| GET | `/api/team/{id}/tasks/export` | `TEAM_VIEW` + `TASK_VIEW` | The filtered list as CSV (at most 10,000 rows), with "Yes", "Late by N days" or "Overdue by N days" |
| GET | `/api/team/{id}/tickets`, `/tickets/summary`, `/tickets/export` | `TEAM_VIEW` + `TICKET_VIEW` | The same for tickets assigned to the person; `onTime` is the resolution SLA, `categoryId` replaces `projectId`, and there are no hours |

Work history filters (all optional): `status` (several; task or ticket statuses plus `OVERDUE`: open and past the due date, or for tickets the SLA clock running past its pause-adjusted due time), `dateField` (`COMPLETED` by default, `DUE`, `START`, `CREATED`; for tickets resolved, resolution due, created) with `from` and `to` (inclusive business dates) or a `preset` resolved against the business today (`THIS_WEEK`, `LAST_WEEK`, `THIS_MONTH`, `LAST_MONTH`, `LAST_3_MONTHS`, `LAST_30_DAYS`; the summary returns the resolved `from`/`to` and `today`), `projectId` / `categoryId`, `priority` (several) and `search`. A Super Admin sees everyone's history, a department manager the people in the departments they manage, an employee only their own (403 `WORK_NOT_VISIBLE` otherwise); each task and ticket still follows its module's visibility.

## Tasks: `/api/tasks`

All task endpoints need `TASK_VIEW`. Scope is enforced on every call:
- **Super Admin:** every task.
- **Department manager:** tasks in their departments.
- **Everyone:** tasks they are assigned to, created or watch.

A task you can't see returns **404**, not 403, so its existence is not revealed. `dueState` (`OVERDUE`, `DUE_TODAY`, `DUE_SOON` within 3 days, `SCHEDULED`, or `NONE` for closed or undated tasks) is computed in the business time zone (`APP_TIME_ZONE`).

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/tasks` | `TASK_VIEW` | Search. Filters: `search` (title or code), `status` (repeatable), `priority` (repeatable), `assigneeId`, `departmentId`, `projectId`, `due` (`OVERDUE` / `TODAY` / `UPCOMING` = next 7 days / `NO_DUE_DATE`, open tasks only), `view` (`ALL` / `ASSIGNED_TO_ME` / `CREATED_BY_ME` / `WATCHING`). Sort fields: `due`, `priority`, `updated`, `created`, `code`, `title`, `status`. Empty values sort last |
| GET | `/api/tasks/my/summary` | `TASK_VIEW` | Counts of my open, overdue, due-today, upcoming (7 days), in-progress and blocked tasks, and tasks completed this week |
| GET | `/api/tasks/{id}` | `TASK_VIEW` | Full detail: checklist, comments, attachments, dependencies, watchers, history, `version`, and the viewer's `permissions` |
| POST | `/api/tasks` | `TASK_CREATE` | Create. The department defaults to yours; you can create in your own department or one you manage. Assigning anyone but yourself needs `TASK_ASSIGN` and someone within your scope. Returns `201` with a code such as `TSK-000042` |
| PUT | `/api/tasks/{id}` | `TASK_EDIT` | Replace editable fields. **Send `version`**: a stale version returns 409 `STALE_UPDATE` |
| PUT | `/api/tasks/{id}/assignee` | `TASK_EDIT` | `{ "assigneeId": 4 }` or `null`. You can take or drop a task yourself; assigning others needs `TASK_ASSIGN` within scope (403 `CANNOT_ASSIGN`) |
| PUT | `/api/tasks/{id}/status` | `TASK_EDIT` | `COMPLETED` stamps `completedAt`. Moving a completed or cancelled task back to an open status reopens it. `CANCELLED` needs `TASK_DELETE` |
| POST / PUT / DELETE | `/api/tasks/{id}/comments[/{commentId}]` | `TASK_VIEW` | Anyone who can see the task can comment. Only the author can edit; the author or an editor can delete |
| POST / PUT / DELETE | `/api/tasks/{id}/checklist[/{itemId}]` | `TASK_EDIT` | Add an item, toggle `{ "done": true }`, or remove it |
| POST / DELETE | `/api/tasks/{id}/watchers[/{userId}]` | `TASK_VIEW` | Anyone can watch or unwatch themselves; adding or removing others needs edit rights. Returns 204 if you unwatch and lose access |
| POST / DELETE | `/api/tasks/{id}/dependencies[/{dependsOnTaskId}]` | `TASK_EDIT` | Rejects self-references (`INVALID_DEPENDENCY`) and loops (`DEPENDENCY_CYCLE`) |
| POST | `/api/tasks/{id}/attachments` | `TASK_EDIT` | Multipart field `file`. Allowed types: PDF, Office, CSV/TXT/MD, PNG/JPG/GIF/WEBP and ZIP, up to `FILE_MAX_SIZE_MB`. The filename is sanitised and the content type comes from the extension |
| GET | `/api/tasks/{id}/attachments/{fileId}` | `TASK_VIEW` | Always served as a download (`Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`) |
| DELETE | `/api/tasks/{id}/attachments/{fileId}` | `TASK_VIEW` | Allowed for the uploader or an editor |

Task changes are written to `task_history`. Creating, assigning and changing the status of a task are also written to `audit_logs`.

## Workload: `/api/workload`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/workload` | `WORKLOAD_VIEW`, or `userId` = yourself | One row per active person in your scope, including people with no tasks (at 0%). Filters: `departmentId`, `userId`, `search`, `status`, `priority` (both repeatable), `from` + `to` (ISO dates, both or neither). Sort: `HIGHEST`, `LOWEST`, `MOST_OVERDUE`, `MOST_COMPLETED`, `MOST_ACTIVE`, `NAME` |

- **Workload %** = remaining hours of open tasks that are overdue, undated or due within `workload.windowDays` (default 14) ÷ (weekly capacity × window weeks) × 100. Remaining hours are max(estimate − logged, 0); an unestimated task counts as `workload.defaultTaskHours` (default 4).
- **Levels:** 0–40 `LOW`, 41–70 `NORMAL`, 71–100 `HIGH`, 101+ `OVERLOADED`.
- **What the filters change:** status, priority and the date range narrow the count columns only. Workload % always uses every open task, so people stay comparable.
- **Completed column:** with a date range, it counts tasks completed in that range; without one, the last 30 days.

## Dashboard: `/api/dashboard`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/dashboard` | `DASHBOARD_VIEW` | One role-aware payload, scoped on the server. It has KPIs (open, due today, overdue, completed this week, open tickets, SLA breaches, pending approvals, team members), the task status mix, 8 weeks of completions with the on-time rate, department workload and performance, the busiest people, overdue and upcoming tasks, recent activity and upcoming events. For a viewer with `MARKETING_VIEW` and company-wide scope, it also carries the Digital Marketing summary. Employees get their own work only |

## Notifications: `/api/notifications`

Any signed-in user, always their own notifications.

| Method | Path | Description |
|---|---|---|
| GET | `/api/notifications` | Newest first. `unread=true` filters to unread |
| GET | `/api/notifications/unread-count` | Count for the bell (polled every minute) |
| POST | `/api/notifications/{id}/read`, `/api/notifications/read-all` | Mark one or all as read |

Notifications are created for task and ticket assignment, task due-soon and overdue (daily, deduplicated), approvals waiting for you or decided, announcements, and recurring marketing activities without a task.

## Calendar: `/api/calendar`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/calendar?from&to&mine` | `CALENDAR_VIEW` | At most 100 days. Merges stored events with the task deadlines, open project milestones and pending approval due dates the viewer can see, plus birthdays, work anniversaries, important days and marketing events (everyone) and scheduled email campaigns (`CAMPAIGN_VIEW`). Those items carry an `occasion` (category, detail, region, event status, LinkedIn post status) |
| GET | `/api/calendar/events/{id}` | `CALENDAR_VIEW` | One event |
| POST / PUT / DELETE | `/api/calendar/events[/{id}]` | `CALENDAR_EDIT` | Company-wide events need a Super Admin, and department events need department management |

Leave is managed in Zoho People and is not part of the calendar.

## Celebrations, important days and events: `/api/celebrations`

Every signed-in user can read these: names, and the day and month of birthdays and anniversaries. Changes need
`CELEBRATIONS_EDIT`, a Digital Marketing permission granted to people (with `MARKETING_VIEW`). A Super Admin holds it
too. Only those users see birth years (`dateOfBirth`) and people who have left. Every change is audited.

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/celebrations/employees?search&status&departmentId` | signed in | Paged (`sort` = `name`, `code`, `joined`, `updated`). Non-editors get active employees only and no `dateOfBirth` |
| GET | `/api/celebrations/employees/{id}` | signed in | One employee, with next birthday, next anniversary and its years |
| GET | `/api/celebrations/employees/export` | `CELEBRATIONS_EDIT` | CSV in the import's columns (holds full birth dates) |
| POST / PUT / DELETE | `/api/celebrations/employees[/{id}]` | `CELEBRATIONS_EDIT` | Linking `userId` fills blank names, designation, reporting manager, department and work email. 409 `EMPLOYEE_CODE_TAKEN`, `EMPLOYEE_USER_TAKEN` |
| GET / PUT / DELETE | `/api/celebrations/employees/{id}/photo` | read: signed in; change: `CELEBRATIONS_EDIT` | PNG, JPG, GIF or WebP; served as a download |
| GET | `/api/celebrations/important-days?search&category&region&recurrence&year` | signed in | Paged (`sort` = `date`, `name`, `updated`), with next occurrence and its LinkedIn post |
| GET | `/api/celebrations/important-days/export` | signed in | CSV in the import's columns |
| POST / PUT / DELETE | `/api/celebrations/important-days[/{id}]` | `CELEBRATIONS_EDIT` | `YEARLY` repeats on the same date, `ONE_OFF` is entered per year. 409 `IMPORTANT_DAY_EXISTS` for the same name, date and region |
| GET / POST / DELETE | `/api/celebrations/important-days/{id}/attachments[/{fileId}]` | read: signed in; change: `CELEBRATIONS_EDIT` | Creatives |
| GET | `/api/celebrations/events?search&type&status&from&to&ownerId` | signed in | Paged (`sort` = `start`, `title`, `updated`). The linked email campaign's details only for `CAMPAIGN_VIEW` |
| POST / PUT / DELETE | `/api/celebrations/events[/{id}]` | `CELEBRATIONS_EDIT` | Types `UPCOMING_EVENT`, `WEBINAR`, `EVENT_MARKETING`, `EMAIL_MARKETING`; links must be http(s) (400 `INVALID_LINK`) |
| GET / POST / DELETE | `/api/celebrations/events/{id}/attachments[/{fileId}]` | read: signed in; change: `CELEBRATIONS_EDIT` | Event files |
| GET | `/api/celebrations/upcoming?days` | signed in | "Coming up": birthdays, anniversaries, important days and events from today through the next `days` (14, at most 90) |
| GET | `/api/celebrations/linkedin-posts?from&to&status&needed&kind` | signed in | Every occurrence in the range (today + 60 days by default, at most 366) with its post status |
| PUT | `/api/celebrations/linkedin-posts` | `CELEBRATIONS_EDIT` | `{ subjectType, subjectId, occurrenceDate, postNeeded, ownerId, status, postDate, postLink, version }`. 404 `OCCURRENCE_NOT_FOUND` when nothing falls on that date |

CSV imports: `employees` and `important-days` through `/api/marketing/imports` (with `CELEBRATIONS_EDIT`).

Reminders: a daily job (`CELEBRATION_REMINDER_CRON`, run and dated in `APP_TIME_ZONE` like the rest of the app)
notifies every active Super Admin and `CELEBRATIONS_EDIT` holder "Reminder days before" days ahead
(setting, default 2) and on the day. Each reminder is sent once (`OCCASION_REMINDER`, deduplicated per occurrence,
lead time and recipient). It is in-app only, because no email sender is configured.

## Tickets: `/api/tickets`

All ticket endpoints need `TICKET_VIEW`. Everyone sees the tickets they raised or are assigned. Agents (`TICKET_EDIT`) see their department's tickets, managers see their departments' tickets, and a Super Admin sees all. Codes look like `TKT-000001`.

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/tickets` | `TICKET_VIEW` | Filters: `search`, `status`, `priority` (repeatable), `categoryId`, `departmentId`, `assigneeId`, `view`. Sort: `created`, `updated`, `priority`, `status`, `due`, `code`, `subject` |
| GET | `/api/tickets/my/summary` | `TICKET_VIEW` | Counts for My Tickets |
| GET | `/api/tickets/categories` | `TICKET_VIEW` | Categories and their handling departments |
| GET | `/api/tickets/{id}` | `TICKET_VIEW` | Detail with SLA clocks (state, remaining, paused), comments (internal notes only for agents), attachments and history |
| POST | `/api/tickets` | `TICKET_CREATE` | Raise a ticket. SLA due times are snapshotted from the priority's policy |
| PUT | `/api/tickets/{id}` | `TICKET_EDIT` | Priority, category, team. Send `version` |
| PUT | `/api/tickets/{id}/assignee` | `TICKET_EDIT` | Assigning others needs `TICKET_ASSIGN` within the department |
| PUT | `/api/tickets/{id}/status` | `TICKET_VIEW` | Agents move a ticket through the workflow. Requesters confirm (close) or reopen a resolved ticket, and managers reopen closed tickets. `WAITING_FOR_REQUESTER` pauses the SLA clock |
| POST / PUT / DELETE | `/api/tickets/{id}/comments[/{commentId}]` | `TICKET_VIEW` | Public replies, or internal notes from agents. The first public reply from anyone but the requester is the first response |
| POST / GET / DELETE | `/api/tickets/{id}/attachments[/{fileId}]` | `TICKET_VIEW` | Same upload rules as task attachments |

## SLA: `/api/sla`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/sla/policies` | `TICKET_VIEW` | First response and resolution targets per priority (defaults: URGENT 1h/4h, HIGH 2h/8h, MEDIUM 4h/24h, LOW 8h/48h) |
| PUT | `/api/sla/policies/{id}` | `SLA_MANAGE` | Edit a target (versioned, audited). Only new tickets use it |
| GET | `/api/sla/summary?days` | `TICKET_VIEW` | Compliance % (met ÷ decided; `null` when nothing is decided), open tickets by state (`ON_TRACK`, `WARNING` from `sla.warningThresholdPct`, `BREACHED`) and the tickets at risk |

## Projects: `/api/projects`

Visible to the project's department, its managers, the owner and the members. Managers of the department and the owner can edit.

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/projects/options` | `PROJECT_VIEW` or `TASK_CREATE` | Open projects (planning, active, on hold) for task pickers |
| GET | `/api/projects` | `PROJECT_VIEW` | Filters: `search`, `status`, `departmentId`. Sort: `name`, `code`, `start`, `end`, `updated`, `status` |
| GET | `/api/projects/{id}` | `PROJECT_VIEW` | Detail: progress (completed ÷ non-cancelled tasks unless overridden; `null` without tasks), members, milestones, risks (severity = probability × impact), dependencies, tasks and documents |
| POST / PUT | `/api/projects[/{id}]` | `PROJECT_EDIT` | Create (`PRJ-` code) or edit. Send `version` |
| POST / DELETE | `/api/projects/{id}/members[/{userId}]` | `PROJECT_EDIT` | Members |
| POST / PUT / DELETE | `/api/projects/{id}/milestones[/{milestoneId}]` | `PROJECT_EDIT` | Milestones (overdue when open and past due) |
| POST / PUT / DELETE | `/api/projects/{id}/risks[/{riskId}]` | `PROJECT_EDIT` | Risks |
| POST / DELETE | `/api/projects/{id}/dependencies[/{dependsOnId}]` | `PROJECT_EDIT` | Dependencies on other projects, with a loop check |

## Approvals: `/api/approvals`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/approvals/types` | `APPROVAL_VIEW` | Request types and their workflow steps |
| POST / PUT | `/api/approvals/types[/{id}]` | `APPROVAL_CONFIGURE` | Create a type (code, name, description, amount rule, steps), or edit or retire one |
| PUT | `/api/approvals/types/{id}/steps` | `APPROVAL_CONFIGURE` | Replace a workflow's steps. A step is decided by the requester's department manager, by a role, or by a named person |
| GET | `/api/approvals` | `APPROVAL_VIEW` | Filters: `search`, `status`, `typeId`. Sort: `created`, `due`, `updated`, `code` |
| GET | `/api/approvals/{id}` | `APPROVAL_VIEW` | Detail with its own copy of the steps and each decision |
| POST | `/api/approvals` | `APPROVAL_VIEW` | Submit (`APR-` code). Nobody approves their own request. A manager's own request, or one from a department without a manager, escalates to a Super Admin |
| POST | `/api/approvals/{id}/decision` | `APPROVAL_DECIDE` | Approve or reject the current step (a reason is needed to reject). Steps run in order |
| POST | `/api/approvals/{id}/cancel` | `APPROVAL_VIEW` | The requester cancels while the request is pending |

## Announcements: `/api/announcements`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/announcements?state` | `DASHBOARD_VIEW` | Announcements addressed to you (everyone, or your department), with read and acknowledgement state. Managers see read counts. Sort: `published` |
| GET | `/api/announcements/unread-count` | `DASHBOARD_VIEW` | Unread count |
| POST / PUT / DELETE | `/api/announcements[/{id}]` | `ANNOUNCEMENT_MANAGE` | Company-wide announcements need a Super Admin. Optional publish time, expiry and "acknowledgement required" |
| POST | `/api/announcements/{id}/read`, `/acknowledge` | `DASHBOARD_VIEW` | Record your read or acknowledgement receipt |

## Knowledge base: `/api/knowledge-base`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/knowledge-base/categories` | `KB_VIEW` | The brief's categories with article counts |
| GET | `/api/knowledge-base/articles` | `KB_VIEW` | Filters: `search` (FULLTEXT with prefix matching, plus a title match), `categoryId`, `status`, `tag`. Sort: `updated`, `published`, `title`, `views`. Drafts and archived articles are visible to editors only |
| GET | `/api/knowledge-base/articles/{slug}` | `KB_VIEW` | Article (Markdown; raw HTML is never rendered) |
| POST / PUT | `/api/knowledge-base/articles[/{id}]` | `KB_EDIT` | Create or edit |
| POST / GET / DELETE | `/api/knowledge-base/articles/{id}/attachments[/{fileId}]` | `KB_EDIT` (download: `KB_VIEW`) | Attachments |

## Documents: `/api/documents`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/documents` | `DOCUMENT_VIEW` | Filters: `search`, `departmentId`, `projectId`. Sort: `updated`, `name`, `created`. Visible company-wide, within the owning department, or to the uploader |
| GET | `/api/documents/{id}` | `DOCUMENT_VIEW` | Detail with every version |
| POST | `/api/documents` | `DOCUMENT_EDIT` | Upload (multipart) as version 1 |
| POST | `/api/documents/{id}/versions` | `DOCUMENT_EDIT` | Upload a new numbered version. Older versions stay downloadable |
| PUT / DELETE | `/api/documents/{id}` | `DOCUMENT_EDIT` | Edit details or delete |
| GET | `/api/documents/{id}/versions/{versionNo}/download` | `DOCUMENT_VIEW` | Download a version |

## Reports: `/api/reports`

Each report needs `REPORT_VIEW` plus the module's view permission; each `/export` needs `REPORT_EXPORT` plus the module's view permission. Reports cover the viewer's scope only. Exports take `format=CSV` (default). `format=PDF` answers 400 `FORMAT_NOT_AVAILABLE` until a PDF exporter is added.

| Method | Path | Module permission | Description |
|---|---|---|---|
| GET | `/api/reports/tasks[/export]` | `TASK_VIEW` | `from`, `to`, `departmentId`, `userId`, `projectId`, `status`. Created, completed, on-time %, open, overdue %, hours logged; by status, department and employee; weekly trend |
| GET | `/api/reports/workload[/export]` | `WORKLOAD_VIEW` | `departmentId`, `userId`. Employee rows from the workload service rolled up per department |
| GET | `/api/reports/tickets[/export]` | `TICKET_VIEW` | `from`, `to`, `departmentId`, `assigneeId`. Created, resolved, open, open breached, SLA compliance, average resolution; by priority and department; ageing and status of open tickets |
| GET | `/api/reports/projects[/export]` | `PROJECT_VIEW` | `departmentId`, `status`. Progress, tasks, milestones, risks, past end date |

## Global search: `/api/search`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/search?q&group&limit` | any signed-in user | Grouped results across tasks, tickets, projects, employees, knowledge base, marketing pages, keywords, email and paid campaigns, and leads. Each group appears only with its module's view permission (marketing groups also need `MARKETING_VIEW`), and is searched through the module's own list service and scope. 5 hits per group by default; `limit` (up to 20) and `group` narrow it for a "see all" view |

## Administration

### Settings: `/api/admin/settings` (`SETTINGS_MANAGE`)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/settings` | Every setting with its value, range, unit and who last changed it |
| PUT | `/api/admin/settings/{key}` | `{ value, version }`. Out-of-range values answer 400 `INVALID_SETTING`, stale versions 409 `STALE_UPDATE`. Applied on the next read and audited |

| Key | Range | Default |
|---|---|---|
| `workload.windowDays` | 1–90 days | 14 |
| `workload.defaultTaskHours` | 0–40 hours | 4 |
| `users.defaultWeeklyCapacityHours` | 1–80 hours | 40 |
| `sla.warningThresholdPct` | 1–99 % | 75 |
| `marketing.target.behindThresholdPct` | 0–100 % | 60 |
| `upload.maxSizeMb` | 1 MB up to the server's multipart limit (`FILE_MAX_SIZE_MB`) | 20 |
| `celebrations.reminderDaysBefore` | 0–30 days (0 = only on the day) | 2 |

### Roles and permissions

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/roles`, `/api/permissions` | `USER_MANAGE` or `PERMISSION_MANAGE` | Role and permission catalogue |
| PUT | `/api/roles/{code}/permissions` | `PERMISSION_MANAGE` and Super Admin | Replace a role's permissions. The Super Admin role is locked (409 `ROLE_LOCKED`). Digital Marketing permissions can't be put on a role. Only held permissions can be granted, and each action permission needs its view permission. Audited with what was added and removed |

### Audit logs: `/api/admin/audit-logs` (`AUDIT_VIEW`)

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/audit-logs` | Newest first. Filters: `action` (repeatable), `module` (also matches the record changes of the module's record types), `event`, `actorId`, `entityType`, `entityId` (with `entityType`, also the record's children), `from`, `to` (business days), `search` (details, record name, actor name or email, IP) |
| GET | `/api/admin/audit-logs/catalog` | Actions grouped by module, the 13 brief section 63 events, the entity types in the log and every `recordTypes` entry (type, label, module), for the filters |
| GET | `/api/admin/audit-logs/export` | Same filters, CSV, up to 5,000 rows |

Entries are read-only: no endpoint edits or deletes them. Each item has `action`, `actionLabel` ("Task edited"), `module`, `actor` (null = the system), `entityType`, `entityTypeLabel`, `entityId`, `entityLabel` (the record's code, title or name when logged), `parentType`/`parentId` (the record it belongs to), `details`, `ipAddress`, `userAgent`, `createdAt`.

Record changes (`RECORD_CREATED`, `RECORD_UPDATED`, `RECORD_DELETED`, `RECORD_RESTORED`, `RECORD_PURGED`) are written automatically for every entity. Their `details.changes` is `{field: {from, to}}`; a reference field adds `ref` (the record type, e.g. `USER`) and, for users and departments, `fromLabel`/`toLabel`. Secrets show only `{redacted: true}`. Money fields carry `finance: true`; without `FINANCE_VIEW` (Super Admins have it) their values are replaced by `hidden: true`, money inside business events is blanked and listed in `details.hiddenFields`, and the free-text search skips entries holding amounts.

### Recycle bin: `/api/recycle-bin`

Deleting a task, announcement, calendar event, employee, important day, marketing event, document, marketing activity, backlink, content item, email or paid campaign, lead, SEO page or keyword, target or target type moves it to the recycle bin: it disappears from every list, search, dashboard and report, and its children (comments, checklist, attachments, versions) stay untouched. Unique values (an employee ID, a page URL, a target type code) stay taken until the permanent delete: reusing one answers 409 `IN_RECYCLE_BIN`. Whoever a module lets delete an item can still delete it, but only holders of `RECYCLE_BIN_MANAGE` (Super Admin only by default) can see the bin (every department) and restore; deleting permanently also needs `RECYCLE_BIN_PURGE` (Super Admin only by default). The one exception is `/undo`: whoever deleted a record may bring it back within two minutes (the "Undo" toast).

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/recycle-bin` | `RECYCLE_BIN_MANAGE` (Super Admin only by default; sees every item); 403 otherwise | Newest first. Filters: `module`, `entityType`, `deletedById`, `from`, `to` (business days of the deletion), `search` (name). Sorts: `deleted`, `name`, `type`. Each item: `entityType`, `entityTypeLabel`, `entityId`, `label`, `module`, `moduleLabel`, `department`, `deletedAt`, `deletedBy`, `purgeOn` (automatic permanent delete), `daysUntilPurge` (business days from today; 0 is tonight), `confirmationText` |
| GET | `/api/recycle-bin/catalog` | Same | Modules, record types and people for the filters, and `retentionDays` |
| GET | `/api/recycle-bin/{id}/restore-check` | `RECYCLE_BIN_MANAGE` | Changes nothing. `{canRestore, blockCode, blockMessage, warnings}`: a clash that would stop the restore (e.g. another employee now has its employee ID) and warnings it survives (e.g. its work email now belongs to another employee or user) |
| POST | `/api/recycle-bin/{id}/restore` | `RECYCLE_BIN_MANAGE` | Puts the record back exactly as it was and answers `{entityType, entityId, label}`. 409 `PARENT_IN_RECYCLE_BIN` (restore the page or target type first) or `RESTORE_CONFLICT` (a live record took its URL, link or employee ID) |
| POST | `/api/recycle-bin/undo` | Signed in | `{entityType, entityId}`. Restores a record the caller deleted in the last two minutes (any time with `RECYCLE_BIN_MANAGE`); answers like restore. 404 when the caller did not delete it, 409 `UNDO_EXPIRED` after two minutes |
| POST | `/api/recycle-bin/{id}/purge` | `RECYCLE_BIN_MANAGE` and `RECYCLE_BIN_PURGE` | `{confirm}` must equal the item's `confirmationText` (case and surrounding spaces ignored), else 400 `CONFIRMATION_MISMATCH`. Deletes the record and its children for good, with their stored files. 409 `PURGE_BLOCKED` while other records still point to it |

Items older than the `recycleBin.retentionDays` setting (Admin Settings, default 90) are permanently deleted every night (`RECYCLE_BIN_PURGE_CRON`, default 02:30 in `APP_TIME_ZONE`). Deletes, restores and permanent deletes appear in the record's history (`RECORD_DELETED`, `RECORD_RESTORED`, `RECORD_PURGED`).

### Backups: `/api/admin/backups` and `/api/admin/data-export`

Nightly encrypted backups of the database and the uploads folder, kept 30 daily and 12 monthly (docs/backup-and-restore.md). Each backup is written to the first target and, when `BACKUP_SECONDARY_TARGET` is set, to a second one; each copy is verified, kept and reported on its own. Every backup, export and restore is audited (`BACKUP_COMPLETED`, `BACKUP_COPY_FAILED`, `BACKUP_FAILED`, `BACKUP_REMOVED`, `DATA_EXPORTED`, `BACKUP_RESTORED`).

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/admin/backups/status` | `BACKUP_VIEW` (Super Admin only by default; 403 otherwise) | `healthy` and `problems` over every copy (`NO_BACKUP`, `STALE` when the last success is older than `maxAgeHours` (26), `LAST_FAILED`, `NOT_CONFIGURED`; the second copy's as `SECONDARY_NO_BACKUP`, `SECONDARY_STALE`, `SECONDARY_LAST_FAILED`), `copies` (one per target: `copy` PRIMARY or SECONDARY, `target`, `healthy`, `problems`, `lastSuccessAt`, `lastStatus`, `lastError`), `lastSuccess`, `lastRun` (first copy), `running`, `scheduleEnabled`, `schedule`, `timeZone`, `nextRunAt`, `target`, `secondaryTarget` (null when none), `keepDaily`, `keepMonthly`, `maxAgeHours` |
| GET | `/api/admin/backups` | `BACKUP_VIEW` | Backup history, newest first (`page`, `size`, `sort=started,desc`): `trigger` (SCHEDULED, MANUAL), `status` (RUNNING, SUCCESS, FAILED), `startedAt`, `finishedAt`, `fileName`, `sizeBytes`, `checksum` (SHA-256 of the encrypted file), `target`, `uploadFiles`, `errorMessage`, `requestedById`, `removedAt` (deleted by retention) for the first copy, and `secondary` (`target`, `status`, `errorMessage`, `removedAt`; null when no second target was configured) |
| POST | `/api/admin/backups/run` | Super Admin role | "Run backup now": 202 with the RUNNING run; the backup continues in the background. 409 `BACKUP_RUNNING` while one is running |
| GET | `/api/admin/data-export` | `DATA_EXPORT` (Super Admin only by default) | "Export all data": a streamed ZIP attachment (`nosniff`) with `tables/<table>.csv` for every table (rows in the recycle bin included, marked `_in_recycle_bin` = yes; password and token hashes redacted), `tables.csv` (row counts), `uploads/` (every uploaded file) and `README.txt` |

### Record history: `/api/history`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/history/{entityType}/{entityId}` | `HISTORY_VIEW` (Super Admin only by default; 403 otherwise), and the viewer must be able to open the record | A record's History tab, newest first (`page`, `size`): its own entries and those of its children (comments, checklist items, attachments, steps, versions). Same item shape as the audit log, without IP address and browser unless the viewer has `AUDIT_VIEW`. 404 `RECORD_NOT_FOUND` when the viewer may not open the record. `AUDIT_VIEW` opens any record's history, including deleted records |

## Digital Marketing

Every `/api/marketing/**` endpoint needs `MARKETING_VIEW` (a URL rule) in addition to the permission listed. Rules and formulas are in [digital-marketing.md](digital-marketing.md). Month filters are `month` + `year` (defaulting to the current business month). `ownerId` narrows to one owner. `/summary` endpoints compare with the month before, or with `compareMonth` + `compareYear`. `/trend` endpoints take `months` (default 6).

### Context, integrations and imports

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/context` | `MARKETING_VIEW` | Business today, default month, selectable years, possible owners, behind threshold |
| GET | `/api/marketing/integrations` | `MARKETING_VIEW` | The data source behind each provider seam (manual for now) |
| GET | `/api/marketing/imports` | `MARKETING_VIEW` | Imports you may run: `seo-rankings`, `email-campaigns`, `paid-campaign-results`, `marketing-leads`, `backlinks`, `employees`, `important-days` (each also needs its module's edit permission) |
| GET | `/api/marketing/imports/{type}/template` | `MARKETING_VIEW` | CSV template |
| POST | `/api/marketing/imports/{type}/preview` | `MARKETING_VIEW` | Multipart `file`. Valid rows, invalid rows with errors, and a `checksum`. Saves nothing |
| POST | `/api/marketing/imports/{type}/commit` | `MARKETING_VIEW` | Multipart `file` + `checksum` (+ `skipInvalid`). Re-validates and imports in one transaction |

### Dashboard and monthly report

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/dashboard` | `MARKETING_VIEW` | `month`, `year`, `ownerId`, `months` (2–24). The month against the month before for every module the viewer can see, plus a monthly trend |
| GET | `/api/marketing/reports/monthly[/export]` | `MARKETING_VIEW` | `month`, `year`, `ownerId`, `live`. The frozen report for the month when there is one (unless `live=true`), otherwise the live one. Export is CSV |
| GET | `/api/marketing/reports/frozen` | `MARKETING_VIEW` | Months with a frozen report |
| POST | `/api/marketing/reports/monthly/{year}/{month}/freeze` | `MARKETING_EDIT` + every marketing view permission | Freeze an ended month (insert-only; 409 `REPORT_FROZEN`, 400 `MONTH_NOT_ENDED`) |

### SEO

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/pages` | `SEO_VIEW` | Filters: `search`, `type`, `status`, `ownerId`, `departmentId`, `month`, `year`. Sort: `title`, `url`, `type`, `status`, `updated`, `created`. Each row has the month's keyword stats |
| GET | `/api/marketing/pages/options` | `SEO_VIEW` | Pages for pickers |
| GET | `/api/marketing/pages/{id}` | `SEO_VIEW` | Page detail with total, top 10, average position, improved, declined and not-ranked keywords for the month |
| GET | `/api/marketing/pages/{id}/rankings` | `SEO_VIEW` | `month`, `year`, `months`. Positions of the page's keywords over time, with the average |
| POST / PUT / DELETE | `/api/marketing/pages[/{id}]` | `SEO_EDIT` | Pages |
| GET | `/api/marketing/keywords` | `SEO_VIEW` | Filters: `search`, `pageId`, `ownerId`, `status`, `device`, `month`, `year`. Sort: `keyword`, `page`, `volume`, `difficulty`, `target`, `updated` |
| GET | `/api/marketing/keywords/{id}` | `SEO_VIEW` | Keyword with its standing in the month |
| POST / PUT / DELETE | `/api/marketing/keywords[/{id}]` | `SEO_EDIT` | Keywords |
| GET | `/api/marketing/rankings[/export]` | `SEO_VIEW` | The ranking table for a month. Filters: `search`, `pageId`, `ownerId`, `status`, `device`, `standing` (`TOP_10`/`RANKING`/`NOT_RANKED`/`NOT_RECORDED`), `minPosition`, `maxPosition`, `movement`. Sort: `best`, `worst`, `improvement`, `decline`, `keyword`, `page`, `volume`, `updated` |
| GET | `/api/marketing/rankings/monthly` | `SEO_VIEW` | Monthly summary (top 3, top 10, 11–20, 21–50, 51–100, not ranked) against the month before |
| POST | `/api/marketing/rankings/monthly` | `SEO_EDIT` | Record one month for many keywords, all or nothing (409 `RANKING_EXISTS` if any is recorded) |
| GET | `/api/marketing/keywords/{id}/rankings` | `SEO_VIEW` | A keyword's monthly history, with whether each month can still be corrected |
| POST | `/api/marketing/keywords/{id}/rankings` | `SEO_EDIT` | Record a month (`position` 1–100 or `null` for not ranked) |
| PUT | `/api/marketing/rankings/{id}` | `SEO_EDIT` | Correct an open month (`version`). Closed months answer 409 `MONTH_LOCKED` except for a Super Admin |

### Targets

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/targets` | `TARGET_VIEW` | `month`, `year`, `ownerId`, `status`. Each target with actual, achievement %, remaining and status |
| GET | `/api/marketing/targets/trend` | `TARGET_VIEW` | `typeId`, `view` (month/quarter/year), `year` |
| GET | `/api/marketing/targets/{id}` | `TARGET_VIEW` | One target |
| POST / PUT / DELETE | `/api/marketing/targets[/{id}]` | `TARGET_EDIT` | One type and month (unique). Locked once the month is closed, except for a Super Admin |
| POST | `/api/marketing/targets/monthly` | `TARGET_EDIT` | Set many types' targets for one month |
| GET | `/api/marketing/target-types?includeInactive` | `TARGET_VIEW` | Target types |
| POST / PUT / DELETE | `/api/marketing/target-types[/{id}]` | `MARKETING_EDIT` | Configure types (actual source, lead source filter, behind threshold override) |

### Recurring activities

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/activities` | `MARKETING_VIEW` | Filters: `search`, `frequency`, `active`, `ownerId`. Sort: `name`, `frequency`, `start`, `updated` |
| GET | `/api/marketing/activities/{id}` | `MARKETING_VIEW` | Activity with its checklist, last completion and next due date |
| GET | `/api/marketing/activities/{id}/occurrences` | `MARKETING_VIEW` | Occurrence history |
| POST / PUT / DELETE | `/api/marketing/activities[/{id}]` | `MARKETING_EDIT` | Activities |
| GET | `/api/marketing/activity-occurrences` | `MARKETING_VIEW` | Filters: `status`, `dueFrom`, `dueTo`, `assigneeId`, `activityId`. Sort: `due`, `period` |
| POST | `/api/marketing/activity-occurrences/{id}/complete`, `/skip`, `/reopen` | `MARKETING_VIEW` (assignee, owner or editor) | Close an occurrence without a task (creating the next one), or reopen it. Occurrences with a task follow their task |

### Email and paid campaigns

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/email-campaigns[/export]` | `CAMPAIGN_VIEW` | Filters: `search`, `type`, `status`, `ownerId`, `month`, `year`. Sort: `date`, `name`, `sent`, `leads`, `status`, `updated`. Rows carry open, click and lead-conversion rates |
| GET | `/api/marketing/email-campaigns/summary`, `/trend` | `CAMPAIGN_VIEW` | Month totals and rates against the comparison month; monthly trend |
| GET / POST / PUT / DELETE | `/api/marketing/email-campaigns/{id}` | `CAMPAIGN_VIEW` / `CAMPAIGN_EDIT` | Campaigns |
| GET | `/api/marketing/paid-campaigns[/export]` | `CAMPAIGN_VIEW` | Filters: `search`, `platform`, `status`, `ownerId`, `month`, `year`. Sort: `start`, `name`, `budget`, `status`, `updated` |
| GET | `/api/marketing/paid-campaigns/summary`, `/trend` | `CAMPAIGN_VIEW` | Spend, impressions, clicks, leads, CTR, CPL, conversion rate and budget; spend against leads over time |
| GET / POST / PUT / DELETE | `/api/marketing/paid-campaigns/{id}` | `CAMPAIGN_VIEW` / `CAMPAIGN_EDIT` | Campaigns with their monthly results and remaining budget |
| PUT | `/api/marketing/paid-campaigns/{id}/results/{year}/{month}` | `CAMPAIGN_EDIT` | Save one month's results (versioned) |

### Leads, backlinks and content

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/marketing/leads[/export]` | `LEAD_VIEW` | Filters: `search`, `source`, `status`, `ownerId`, `month`, `year`, `emailCampaignId`, `paidCampaignId`, `contentItemId`. Sort: `date`, `name`, `company`, `status`, `source`, `code`, `updated` |
| GET | `/api/marketing/leads/summary`, `/trend` | `LEAD_VIEW` | Leads by source against the Website Leads and per-source targets; trend by source |
| GET | `/api/marketing/leads/link-options?kind&search` | `LEAD_VIEW` | Campaigns or content a lead can name |
| GET / POST / PUT / DELETE | `/api/marketing/leads/{id}`, `PUT …/{id}/status` | `LEAD_VIEW` / `LEAD_EDIT` | Leads (`LEAD-` codes) |
| GET | `/api/marketing/backlinks[/export]` | `BACKLINK_VIEW` | Filters: `search`, `status`, `linkType`, `ownerId`, `targetPageId`, `month`, `year`, `stage` (which stage date falls in the month). Sort: `updated`, `code`, `domain`, `authority`, `submitted`, `approved`, `live`, `status` |
| GET | `/api/marketing/backlinks/summary`, `/trend` | `BACKLINK_VIEW` | Target, submitted, approved, live, remaining |
| GET / POST / PUT / DELETE | `/api/marketing/backlinks/{id}`, `PUT …/{id}/status` | `BACKLINK_VIEW` / `BACKLINK_EDIT` | Backlinks (`BLK-` codes); stage rules are enforced on every change |
| GET | `/api/marketing/content[/export]` | `CONTENT_VIEW` | Filters: `search`, `status`, `contentType`, `ownerId`, `authorId`, `month`, `year`, `dateField`. Sort: `updated`, `title`, `published`, `planned`, `traffic`, `cta`, `status` |
| GET | `/api/marketing/content/summary`, `/trend` | `CONTENT_VIEW` | Blog target, published, remaining, leads |
| GET / POST / PUT / DELETE | `/api/marketing/content/{id}`, `PUT …/{id}/status` | `CONTENT_VIEW` / `CONTENT_EDIT` | Content items; publishing rules are enforced on every change |
| GET / POST / DELETE | `/api/marketing/content/{id}/attachments[/{fileId}]` | `CONTENT_VIEW` / `CONTENT_EDIT` | Attachments |

## Actuator

| Path | Auth |
|---|---|
| `/actuator/health` | public (no details). `WARNING` (still HTTP 200) when the last successful backup is older than 26 hours, the last backup failed, or backups are not configured |
| `/actuator/info` | public |
