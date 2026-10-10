-- Phase 24: indexes for the scans that grow with history (found with EXPLAIN on every dashboard, report and list query).

-- Dashboard and task-report KPIs read only active tasks and tasks completed since a date.
CREATE INDEX idx_tasks_status_completed ON tasks (status, completed_at);

-- Task report: tasks created in the period, and the weekly created/completed trend.
CREATE INDEX idx_tasks_created_at ON tasks (created_at);

-- Default list orders (newest first, then id). The existing (lead_date, source) index cannot serve
-- "lead_date desc, id desc", so every page of the leads list sorted the whole table.
CREATE INDEX idx_marketing_leads_date ON marketing_leads (lead_date);
CREATE INDEX idx_backlinks_updated ON backlinks (updated_at);
CREATE INDEX idx_content_items_updated ON content_items (updated_at);

-- Global search pages each module newest first (updated_at) with a contains-match that no index can serve; with this
-- index the leads group walks the newest rows and stops after its five hits instead of sorting every lead.
CREATE INDEX idx_marketing_leads_updated ON marketing_leads (updated_at);
