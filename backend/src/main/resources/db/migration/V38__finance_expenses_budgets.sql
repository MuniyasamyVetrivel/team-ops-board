-- V38: Finance Tracker, part 2 (Expansion Phase X8): expenses, department budgets and the default currency setting.
--
-- 1. finance_expenses: date, category, vendor (Vendors, X7), description, amount (the total, GST included) and the
--    GST in it, currency, department, a receipt (through files / FileStorageService), an optional Approval, notes.
--    The brief asks for the AUD equivalent at a stored exchange rate, so each expense keeps the rate used
--    (aud_rate, 1 for AUD), the date that rate took effect (aud_rate_date, NULL for AUD) and amount_aud: a snapshot at
--    the expense date that later rate changes do not alter. It is taken again when the date or currency changes.
--    Records people delete (recycle bin); the receipt file goes only with the permanent delete.
-- 2. finance_budgets: a department's budget for a month, in AUD (the base currency), unique per department and
--    month. Actual spend, remaining, used % and over-budget are computed from the expenses' AUD amounts, never
--    stored.
-- 3. finance.defaultCurrency (Admin settings → Finance): the currency new licences and expenses start with; INR, AUD,
--    USD or any currency in the exchange rates table. Each record may still choose its own.

CREATE TABLE finance_expenses (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    expense_date    DATE          NOT NULL,
    -- SOFTWARE, HARDWARE, RECRUITMENT, MARKETING, TRAVEL, TRAINING, OFFICE or OTHER.
    category        VARCHAR(32)   NOT NULL,
    vendor_id       BIGINT        NULL,
    description     VARCHAR(500)  NOT NULL,
    amount          DECIMAL(14,2) NOT NULL,
    gst             DECIMAL(14,2) NULL,
    currency        VARCHAR(3)    NOT NULL DEFAULT 'INR',
    aud_rate        DECIMAL(18,8) NOT NULL,
    aud_rate_date   DATE          NULL,
    amount_aud      DECIMAL(14,2) NOT NULL,
    department_id   BIGINT        NULL,
    receipt_file_id BIGINT        NULL,
    approval_id     BIGINT        NULL,
    notes           TEXT          NULL,
    version         INT           NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by      BIGINT        NULL,
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      BIGINT        NULL,
    deleted_at      DATETIME(6)   NULL,
    deleted_by      BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_expenses_receipt (receipt_file_id),
    KEY idx_finance_expenses_date (expense_date),
    KEY idx_finance_expenses_department_date (department_id, expense_date),
    KEY idx_finance_expenses_category (category),
    KEY idx_finance_expenses_vendor (vendor_id),
    KEY idx_finance_expenses_approval (approval_id),
    KEY idx_finance_expenses_deleted (deleted_at),
    CONSTRAINT fk_finance_expenses_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_finance_expenses_department FOREIGN KEY (department_id) REFERENCES departments (id)
        ON DELETE SET NULL,
    CONSTRAINT fk_finance_expenses_receipt FOREIGN KEY (receipt_file_id) REFERENCES files (id) ON DELETE SET NULL,
    CONSTRAINT fk_finance_expenses_approval FOREIGN KEY (approval_id) REFERENCES approvals (id) ON DELETE SET NULL,
    CONSTRAINT ck_finance_expenses_amount CHECK (amount >= 0),
    CONSTRAINT ck_finance_expenses_gst CHECK (gst IS NULL OR (gst >= 0 AND gst <= amount)),
    CONSTRAINT ck_finance_expenses_rate CHECK (aud_rate > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE finance_budgets (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    department_id BIGINT        NOT NULL,
    budget_year   INT           NOT NULL,
    budget_month  INT           NOT NULL,
    -- In AUD, the base currency.
    amount        DECIMAL(14,2) NOT NULL,
    notes         TEXT          NULL,
    version       INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by    BIGINT        NULL,
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT        NULL,
    deleted_at    DATETIME(6)   NULL,
    deleted_by    BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_budgets_department_month (department_id, budget_year, budget_month),
    KEY idx_finance_budgets_month (budget_year, budget_month),
    KEY idx_finance_budgets_deleted (deleted_at),
    CONSTRAINT fk_finance_budgets_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT ck_finance_budgets_month CHECK (budget_month BETWEEN 1 AND 12),
    CONSTRAINT ck_finance_budgets_amount CHECK (amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO app_settings (setting_key, setting_value, value_type, description) VALUES
    ('finance.defaultCurrency', 'INR', 'STRING',
     'The currency new licences and expenses start with; each record may choose its own');
