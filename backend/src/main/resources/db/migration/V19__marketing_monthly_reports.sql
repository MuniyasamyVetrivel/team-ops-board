-- V19: frozen Digital Marketing monthly reports (brief sections 50 and 55). Once a month has ended its report can be
-- frozen: the team-wide report as it stood is kept as JSON, so later corrections never rewrite what was reported.
-- Insert-only, one per month.

CREATE TABLE marketing_monthly_reports (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    report_month INT         NOT NULL,
    report_year  INT         NOT NULL,
    payload      JSON        NOT NULL,
    generated_by BIGINT      NULL,
    generated_at DATETIME(6) NOT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_marketing_monthly_reports_period (report_year, report_month),
    CONSTRAINT fk_marketing_monthly_reports_generated_by FOREIGN KEY (generated_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_marketing_monthly_reports_month CHECK (report_month BETWEEN 1 AND 12)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
