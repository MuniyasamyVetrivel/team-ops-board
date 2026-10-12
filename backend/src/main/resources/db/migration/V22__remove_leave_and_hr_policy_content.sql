-- Leave is managed in Zoho People, so the calendar no longer records it, and the leave-policy HR content goes too.
-- Earlier migrations stay as they ran; this one removes the data and the column that only leave used.

-- Leave entries, then the "person on leave" column (no other event type names a person).
DELETE FROM calendar_events WHERE event_type = 'LEAVE';

ALTER TABLE calendar_events
    DROP FOREIGN KEY fk_calendar_events_user;

ALTER TABLE calendar_events
    DROP INDEX idx_calendar_events_user,
    DROP COLUMN user_id;

-- Leave-policy content from earlier development seeds (matched by the titles they were seeded with; a database that
-- never had them is unaffected). Child rows (reads, tags, comments, history, versions) cascade.
DELETE n FROM notifications n
    JOIN announcements a ON n.entity_type = 'ANNOUNCEMENT' AND n.entity_id = a.id
    WHERE a.title = 'Updated leave policy for 2027';
DELETE FROM announcements WHERE title = 'Updated leave policy for 2027';

DELETE FROM knowledge_articles WHERE title = 'Leave policy and carry-over rules';

DELETE n FROM notifications n
    JOIN tickets t ON n.entity_type = 'TICKET' AND n.entity_id = t.id
    WHERE t.subject = 'Clarify new leave policy for probation';
DELETE FROM tickets WHERE subject = 'Clarify new leave policy for probation';

DELETE n FROM notifications n
    JOIN tasks t ON n.entity_type = 'TASK' AND n.entity_id = t.id
    WHERE t.title = 'Update leave policy';
DELETE FROM tasks WHERE title = 'Update leave policy';

-- The leave application form: its versions cascade with the document, then their file records go.
CREATE TEMPORARY TABLE leave_form_files AS
    SELECT v.file_id FROM document_versions v JOIN documents d ON d.id = v.document_id
    WHERE d.name = 'Leave application form';
DELETE FROM documents WHERE name = 'Leave application form';
DELETE FROM files WHERE id IN (SELECT file_id FROM leave_form_files);
DROP TEMPORARY TABLE leave_form_files;

-- The "leave" tag, when nothing else uses it.
DELETE FROM tags
    WHERE name = 'leave'
      AND NOT EXISTS (SELECT 1 FROM article_tags at WHERE at.tag_id = tags.id)
      AND NOT EXISTS (SELECT 1 FROM task_tags tt WHERE tt.tag_id = tags.id);

-- Reference descriptions that promised leave.
UPDATE ticket_categories SET description = 'Policies and HR documents'
    WHERE name = 'HR Query' AND description = 'Policies, leave and HR documents';
UPDATE knowledge_categories SET description = 'Policies and benefits'
    WHERE slug = 'hr' AND description = 'Policies, leave and benefits';
