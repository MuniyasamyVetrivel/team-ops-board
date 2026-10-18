-- V39: receipts' audit entries belong to their expense (Expansion Phase X8).
--
-- A receipt is a row in files, audited as a FILE record with its file name. From now on its audit entries are filed
-- under the expense (parent_type / parent_id), so they are finance data too and stay hidden, with the file name, from
-- everyone without FINANCE_VIEW (audit log, history, "Export all data"). This files the entries written before:
-- every file an expense's receipt events name (EXPENSE_RECEIPT_ADDED / _REMOVED carry its id), including files
-- deleted since.

UPDATE audit_logs a
JOIN (SELECT DISTINCT r.entity_id AS expense_id,
             CAST(JSON_UNQUOTE(JSON_EXTRACT(r.details, '$.fileId')) AS SIGNED) AS file_id
      FROM audit_logs r
      WHERE r.entity_type = 'FINANCE_EXPENSE'
        AND r.action IN ('EXPENSE_RECEIPT_ADDED', 'EXPENSE_RECEIPT_REMOVED')
        AND JSON_EXTRACT(r.details, '$.fileId') IS NOT NULL) receipts
  ON a.entity_type = 'FILE' AND a.entity_id = receipts.file_id
SET a.parent_type = 'FINANCE_EXPENSE', a.parent_id = receipts.expense_id
WHERE a.parent_type IS NULL;
