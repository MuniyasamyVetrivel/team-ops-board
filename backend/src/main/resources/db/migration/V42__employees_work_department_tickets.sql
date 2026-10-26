-- V42: Employees work on their department's tickets.
--
-- Any employee can raise a ticket to another department (e.g. IT or HR). TicketAccess lets TICKET_EDIT holders in the
-- handling department see its tickets and pick them up, but V2 gave the EMPLOYEE role only TICKET_VIEW and
-- TICKET_CREATE, so a plain IT employee never saw tickets sent to IT. The grant is still scoped by TicketAccess: their
-- own department's tickets and tickets assigned to them. Skipped if a Super Admin already added it to the role.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'TICKET_EDIT'
WHERE r.code = 'EMPLOYEE'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
