-- V35: Project Allocation and Bench Management, part 2 (Expansion Phase X6): allocations.
--
-- bench_allocations: a consultant allocated to a client engagement, with a role, dates, an allocation percentage
-- (1-100 per allocation) and whether it is billable. A consultant's total may go above 100% for a period; the app warns
-- but allows it. Everything else is computed, never stored: the consultant's status (allocated, partially allocated,
-- on bench), the bench (below 100% today), its start date and days on bench, utilisation and the roll-off reminders
-- 30 and 14 days before an allocation ends. Consultants and engagements with live allocations cannot be deleted.

CREATE TABLE bench_allocations (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    consultant_id      BIGINT       NOT NULL,
    engagement_id      BIGINT       NOT NULL,
    role               VARCHAR(150) NOT NULL,
    start_date         DATE         NOT NULL,
    -- Open-ended when NULL (only on an engagement without an end date).
    end_date           DATE         NULL,
    allocation_percent INT          NOT NULL,
    billable           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes              TEXT         NULL,
    version            INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by         BIGINT       NULL,
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by         BIGINT       NULL,
    deleted_at         DATETIME(6)  NULL,
    deleted_by         BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_bench_allocations_consultant_dates (consultant_id, start_date, end_date),
    KEY idx_bench_allocations_engagement (engagement_id),
    KEY idx_bench_allocations_end (end_date),
    KEY idx_bench_allocations_deleted (deleted_at),
    CONSTRAINT fk_bench_allocations_consultant FOREIGN KEY (consultant_id) REFERENCES bench_consultants (id),
    CONSTRAINT fk_bench_allocations_engagement FOREIGN KEY (engagement_id) REFERENCES bench_client_engagements (id),
    CONSTRAINT ck_bench_allocations_percent CHECK (allocation_percent BETWEEN 1 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
