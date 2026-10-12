# Calendar: Celebrations, Important Days and Marketing Events

Leave is managed in Zoho People. Remove every leave feature from the app (calendar event type, leave entries, filters, legend, subtitle text "and leave", approvals, seed data, tests, docs). Do not edit migrations that already ran; add a new Flyway migration.

## Who can do what
- Create, edit and delete: SUPER_ADMIN and Digital Marketing users with a new permission CELEBRATIONS_EDIT
- View: every signed-in user can see names, day and month of birthdays, anniversaries, important days and events
- Birth year and full birth date: visible only to SUPER_ADMIN and CELEBRATIONS_EDIT users (privacy)
- Backend enforces all of this, not just the UI
- Audit every create, update and delete

## Employee record (for birthdays and work anniversaries)
- Required: first name, last name, employee ID (unique), date of birth, designation, reporting manager
- Required for anniversaries: date of joining
- Optional: department, work email, office location, photo, link to an existing app user (auto-fills name, designation, manager)
- Employees do not need to be app users (most staff and consultants won't log in)
- Status: Active / Left (people who left stop appearing)
- Birthdays and anniversaries repeat every year automatically, computed from the dates, never re-entered
- Anniversary shows the number of years ("5 years at Tritusa")
- 29 February birthdays show on 28 February in non-leap years
- CSV import and export of employees with validation (valid rows, invalid rows, errors; nothing bad saved silently)

## Important days (marketing)
- Required: name, date, category, region
- Categories: Festival, National day, Industry day (for example World Quality Day), Company milestone, Other
- Region: Australia, India, Global
- Repeat: every year on the same date, or one-off (for festivals whose date changes each year, such as Diwali, enter each year)
- Optional: description, marketing notes, creative attachment
- CSV import and export

## Events
- Types: Upcoming event, Webinar, Event marketing, Email marketing
- Required: title, type, start date and time, owner
- Optional: end date and time, location or online link, registration link, description, linked email campaign (from the Email Campaigns module), attachments
- Status: Planned, Confirmed, Completed, Cancelled
- Scheduled email campaigns from the Email Campaigns module also appear on the calendar automatically as "Email marketing"

## LinkedIn post tracking (for important days, events, birthdays and anniversaries)
- Fields: post needed (yes/no), post owner, post status (Not started, Drafted, Scheduled, Posted), post date, post link
- A "LinkedIn posts" list showing everything coming up with its post status, so nothing is missed

## Reminders
- Daily scheduled job in business time (Australia/Sydney)
- 2 days before each birthday, work anniversary, important day and event: notify all SUPER_ADMIN and CELEBRATIONS_EDIT users
- Also notify on the day itself
- Days-before is a setting (default 2) in Admin Settings
- In-app bell notifications; use email too if email sending is already configured
- Message examples: "Reminder: Priya Menon's birthday is in 2 days (14 October)", "Reminder: Diwali (India) is in 2 days. LinkedIn post: Not started"
- Never send the same reminder twice (idempotent)

## Calendar UI
- New categories, each with its own icon and colour plus a text label: Birthday, Work anniversary, Important day, Event, Webinar, Event marketing, Email marketing
- Filter chips to show or hide each category, and a legend
- Month, Week and Agenda views keep working
- Clicking an item opens a drawer with its details and LinkedIn post status
- Dashboard widget: "Coming up in the next 14 days" (birthdays, anniversaries, important days, events)
- New pages under Digital Marketing (or Collaboration): Employees and celebrations, Important days, Events, LinkedIn posts
- Follow the existing design system and UI revamp rules

## Dev seed data
- About 10 sample employees with birthdays and joining dates spread over the next 2 months (one within 2 days, to test reminders)
- Important days: Diwali, Australia Day, World Quality Day, Christmas, ANZAC Day, Independence Day (India)
- 2 webinars and 1 event