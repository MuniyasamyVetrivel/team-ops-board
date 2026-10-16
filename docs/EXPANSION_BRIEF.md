# Expansion Brief — Team Ops Board

## Access (applies to every phase below)
- **Phase X4 (employee ID and profile photo) is for everyone.** All signed-in users see employee IDs and photos. Each user can change their own photo. Only SUPER_ADMIN can edit employee IDs and other people's photos.
- **Every other feature in this brief is SUPER_ADMIN only by default:** X1 History tab and Audit Logs, X2 Recycle Bin (view, restore, permanent delete), X3 backup status and Export all data, X5 module settings, X6 Project Allocation and Bench, X7 Vendors, X8 Finance Tracker, and all their reminders and notifications.
- Other users can still delete items they're allowed to delete in their module. Deleted items go to the Recycle Bin, which only SUPER_ADMIN can see.
- Each module keeps its own permission, granted to no role except SUPER_ADMIN, so the Super Admin can grant it to someone later from Admin settings (Roles or a user's permissions):

  | Feature | Permission(s) |
  |---|---|
  | X1 History tab | `HISTORY_VIEW` |
  | X1 Audit Logs | `AUDIT_VIEW` |
  | X2 Recycle Bin: view (every department) and restore | `RECYCLE_BIN_MANAGE` |
  | X2 Recycle Bin: permanent delete | `RECYCLE_BIN_PURGE` (with `RECYCLE_BIN_MANAGE`) |
  | X3 Backup status | `BACKUP_VIEW` |
  | X3 Export all data | `DATA_EXPORT` |
  | X5 Module settings | `MODULE_MANAGE` |
  | X6 Project Allocation and Bench | `BENCH_VIEW`, `BENCH_EDIT` |
  | X7 Vendors | `VENDOR_VIEW`, `VENDOR_EDIT` |
  | X8 Finance Tracker | `FINANCE_VIEW`, `FINANCE_EDIT` |

  Reminders and notifications go only to holders of the module's permission.
- Enforced on the backend (`@PreAuthorize` on the permission; direct API calls without it get **403**). The frontend hides the menu items and tabs, but that is not the security.
- Each phase adds its permissions in the migration of the phase that first needs them. `HISTORY_VIEW` and `RECYCLE_BIN_MANAGE` were added in V28, `RECYCLE_BIN_PURGE` in V29; `AUDIT_VIEW` and `FINANCE_VIEW` already existed.

## Phase X1 — Audit history on every record
- Every table gets created_at, created_by, updated_at, updated_by (backfill existing rows with the system user)
- Every create, edit, delete, restore and permanent delete across ALL entities is logged automatically (one generic mechanism, not per module)
- Each log entry stores: who, when (exact timestamp), action, entity type and ID, and a field-by-field before → after diff
- Sensitive fields (passwords, tokens) are never logged; finance amounts are logged but only visible to users who can see finance
- Every record's drawer or page has a "History" tab showing its timeline (`HISTORY_VIEW`; hidden and 403 for everyone else)
- The Audit Logs admin page (`AUDIT_VIEW`) can filter by user, module, entity, action and date range, with CSV export
- Logs cannot be edited or deleted from the app

## Phase X2 — Recycle bin
- Soft delete everywhere: deleting sets deleted_at and deleted_by instead of removing the row
- Deleted items disappear from lists, search, dashboards and reports
- Recycle Bin page under Administration (`RECYCLE_BIN_MANAGE`): sees every deleted item in every department; filter by module, deleted by and date, and search by name; shows days until the automatic delete. Anyone allowed to delete an item can still delete it; only the bin is restricted
- Restore puts the item back exactly as it was, including its children (for example a task with its comments, checklist and attachments)
- Restore needs `RECYCLE_BIN_MANAGE`; permanent delete needs `RECYCLE_BIN_PURGE` as well and asks for a confirmation that names the item
- After a delete anywhere in the app, a toast offers "Undo" for a few seconds; whoever deleted the item may undo it for two minutes without the bin permissions
- Automatic permanent delete after a configurable number of days (default 90) in Admin Settings
- Unique fields (such as email and employee ID) are released only on permanent delete; restore checks for clashes first (a clash on a unique value such as the employee ID blocks it; a reused work email is a warning)

## Phase X3 — Backups and "never lose data"
- Nightly encrypted backup of the database AND uploaded files
- Stored off the app server in object storage (Lightsail object storage or S3), plus an optional second copy location
- Retention: 30 daily, 12 monthly
- Restore script and step-by-step restore guide; a documented monthly restore test
- Admin "Backup status" page (`BACKUP_VIEW`): last successful backup time, size, next run, and history; red alert in-app if the last backup is older than 26 hours or failed
- "Export all data" button (`DATA_EXPORT`): a ZIP of every table as CSV plus all uploaded files, so data is usable even without the app
- Break-glass access: a second Super Admin account and a documented recovery procedure so nobody is ever locked out
- Backups and exports are audited

## Phase X4 — Employee ID and profile photo
- Employee ID: required, unique, editable only by Super Admin; every signed-in user sees it in the Team directory, profile, search and exports
- Link app users to Employee records (celebrations) by employee ID so details are stored once
- Profile photo: JPG, PNG or WebP, max 2 MB, square crop in the browser, resized on the server, stored with the existing storage abstraction
- Users can change their own photo; only Super Admin can change anyone else's
- Initials avatar as the fallback; photos appear everywhere avatars appear

## Phase X5 — Department modules framework
- A module registry: each tool (for example Bench Management, Vendors, Finance) is a module with a key, name, icon, owning department(s) and its own VIEW and EDIT permissions
- Super Admin (`MODULE_MANAGE`) can switch modules on or off per department in Admin Settings
- The sidebar shows a "Department tools" section with only the modules enabled for the user's department(s) and permitted to them (by default only Super Admin holds module permissions)
- Backend enforces the module switch and permissions on every endpoint
- Documented recipe (docs/adding-a-module.md) so new modules can be added later in a standard way

## Phase X6 — Project Allocation and Bench Management (HR, Talent Acquisition)
- Consultants: linked to an Employee record, with skills (for example SAP S/4HANA, Tosca, test automation, performance testing), certifications, experience, location, availability date, and status (Allocated, Partially allocated, On bench, On leave of absence, Notice period, Left)
- Clients and client engagements (separate from internal Projects): client name, engagement name, location, start and end dates, account manager
- Allocations: consultant, engagement, role, start and end dates, allocation %, billable yes/no
- Bench: anyone below 100% allocation or with no allocation; bench start date and days on bench
- Roll-off alerts 30 and 14 days before an allocation ends, to holders of `BENCH_VIEW` (Super Admin only by default)
- Dashboard: utilisation %, billable vs non-billable, on bench now, days on bench, upcoming roll-offs, skills available on the bench
- Read-only access (for example for Pre-Sales) is a `BENCH_VIEW` grant from Admin settings; nobody but Super Admin has it by default
- Overlap check: total allocation above 100% is warned
- CSV import and export

## Phase X7 — Vendor Management
- Vendors: name, ABN / GST / tax ID, category (Software, Hardware, Recruitment agency, Marketing, Services, Other), country, address, website, payment terms, status (Active, Inactive, Blacklisted), rating and notes
- Contacts per vendor: name, role, email, phone
- Contracts: title, start and end date, value and currency, renewal date, auto-renew yes/no, notice period, documents
- Renewal reminders 30 and 7 days before, to holders of `VENDOR_VIEW` (Super Admin only by default)
- A vendor page shows its contracts, linked licences and expenses
- CSV import and export

## Phase X8 — Finance Tracker (Super Admin only by default; FINANCE_VIEW / FINANCE_EDIT can be granted)
- Tools and licences: name, vendor, owner, department, seats paid, seats used (assigned users), billing cycle (monthly, yearly, one-off), cost, currency, start date, renewal date, payment method label (never full card numbers; last 4 digits at most), status (Active, Trial, Cancelled)
- Expenses: date, category (Software, Hardware, Recruitment, Marketing, Travel, Training, Office, Other), vendor, description, amount, currency, GST, department, receipt attachment, optional link to an Approval
- Multi-currency: store the original amount and currency plus an AUD equivalent at a stored exchange rate (manual rates table, editable by finance users)
- Budgets per department per month, with actual vs budget
- Dashboard: this month's spend, monthly trend, spend by department and category, upcoming renewals (30 days), unused or under-used licences (seats used below 70%), total annualised software cost
- Renewal reminders 30 and 7 days before, to holders of `FINANCE_VIEW` (Super Admin only by default)
- Finance data is hidden from everyone without FINANCE_VIEW, including in search, exports and audit details
- CSV export

## Rules for all phases
- Follow CLAUDE.md, the design system and the UI revamp rules
- Every new module uses the audit history, recycle bin and module framework from X1, X2 and X5
- Every new module follows the Access rules above: its own permissions, granted to SUPER_ADMIN only, 403 on the API without them, hidden in the UI
- Backend first, then frontend, for each phase; tests for permissions and business rules
- Integration tests use team_ops_board_it, never the real database