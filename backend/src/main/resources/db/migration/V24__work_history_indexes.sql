-- Team member work history (/api/team/{userId}/tasks and /tickets): one person's work over years, filtered by status
-- and a completed / due date range. Each index starts with the assignee, so it also serves the assignee foreign key,
-- and the older (assignee_id, status) indexes it extends are dropped.

CREATE INDEX idx_tasks_assignee_status_completed ON tasks (assignee_id, status, completed_at);
CREATE INDEX idx_tasks_assignee_due ON tasks (assignee_id, due_date);
DROP INDEX idx_tasks_assignee_status ON tasks;

CREATE INDEX idx_tickets_assignee_status_resolved ON tickets (assignee_id, status, resolved_at);
CREATE INDEX idx_tickets_assignee_resolution_due ON tickets (assignee_id, resolution_due_at);
DROP INDEX idx_tickets_assignee_status ON tickets;
