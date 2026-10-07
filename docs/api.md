# API Reference

Base path: `/api`. JSON in and out. Every endpoint except the public auth endpoints requires `Authorization: Bearer <accessToken>`.

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
| 409 | e.g. `EMAIL_IN_USE` | Conflict with existing data |
| 500 | `INTERNAL_ERROR` | Unexpected error. Details are logged server-side, never returned |

## Authentication: `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Email + password. Returns an access token and sets the refresh cookie |
| POST | `/api/auth/refresh` | refresh cookie | Rotates the refresh cookie and returns a new access token |
| POST | `/api/auth/logout` | refresh cookie (optional) | Revokes the refresh token and clears the cookie. Always `204` |
| GET | `/api/auth/me` | bearer | Current user, department, roles and effective permissions |

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
| POST | `/api/users` | `USER_MANAGE` | Create. The body includes `password` (8+ characters with a letter and a number), `roles` and `permissions`. Returns `201` |
| PUT | `/api/users/{id}` | `USER_MANAGE` | Edit profile fields: email, names, job title, contact details, department, `reportsToId`, `weeklyCapacityHours` |
| PUT | `/api/users/{id}/access` | `PERMISSION_MANAGE` | Replace `roles` and direct `permissions`. Audited with before and after values |
| POST | `/api/users/{id}/disable` / `enable` | `USER_MANAGE` | Disabling revokes every session immediately |
| POST | `/api/users/{id}/reset-password` | `USER_MANAGE` | `{ "newPassword": "…" }`. Signs the user out everywhere. Returns `204` |
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

## Projects: `/api/projects`

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/projects/options` | `PROJECT_VIEW` or `TASK_CREATE` | Open projects (planning, active, on hold) for task pickers. Full project management arrives in Phase 8 |

## Actuator

| Path | Auth |
|---|---|
| `/actuator/health` | public (no details) |
| `/actuator/info` | public |
