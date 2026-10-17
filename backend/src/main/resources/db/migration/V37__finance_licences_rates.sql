-- V37: Finance Tracker, part 1 (Expansion Phase X8), as the "finance" department tool (X5 module framework):
-- tools and licences, and the manual exchange rates table.
--
-- 1. finance_licences: a tool or licence with its vendor (Vendors, X7), owner, department, seats paid, billing cycle
--    (MONTHLY, YEARLY, ONE_OFF), cost and currency, start and renewal dates, a payment method label and at most the
--    last 4 digits of a card (never a full card number), status (ACTIVE, TRIAL, CANCELLED) and notes. The name is
--    unique, including licences in the recycle bin. Never stored, always computed: seats used (assigned users), free
--    seats, seat utilisation and under-use, the annual cost, its AUD equivalent and the days until renewal.
--    vendor_id is a plain column (the vendor may be in the recycle bin while the licence is too).
-- 2. finance_licence_assignments: the users a licence's seats are assigned to. Children of the licence: they stay
--    while it is in the recycle bin and go with its permanent delete.
-- 3. finance_exchange_rates: manual rates to AUD, the base currency. One unit of `currency` is `rate_to_aud` AUD from
--    `effective_from` until the next rate for that currency. AUD itself is never stored (always 1).
-- 4. FINANCE_EDIT (FINANCE_VIEW exists since V26), on SUPER_ADMIN only, and the module switched on for HR where that
--    department exists. Renewal reminders go out 30 and 7 days before a licence renews.

CREATE TABLE finance_licences (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(200)  NOT NULL,
    vendor_id        BIGINT        NULL,
    owner_id         BIGINT        NULL,
    department_id    BIGINT        NULL,
    seats_paid       INT           NULL,
    -- MONTHLY, YEARLY or ONE_OFF.
    billing_cycle    VARCHAR(16)   NOT NULL,
    cost             DECIMAL(14,2) NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'INR',
    start_date       DATE          NULL,
    renewal_date     DATE          NULL,
    -- A label such as "Corporate Amex"; never a card number.
    payment_method   VARCHAR(60)   NULL,
    card_last4       VARCHAR(4)    NULL,
    -- ACTIVE, TRIAL or CANCELLED; set by people.
    status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    notes            TEXT          NULL,
    version          INT           NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by       BIGINT        NULL,
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by       BIGINT        NULL,
    deleted_at       DATETIME(6)   NULL,
    deleted_by       BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_licences_name (name),
    KEY idx_finance_licences_vendor (vendor_id),
    KEY idx_finance_licences_owner (owner_id),
    KEY idx_finance_licences_department (department_id),
    KEY idx_finance_licences_status (status),
    KEY idx_finance_licences_renewal (renewal_date),
    KEY idx_finance_licences_deleted (deleted_at),
    CONSTRAINT fk_finance_licences_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT fk_finance_licences_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_finance_licences_department FOREIGN KEY (department_id) REFERENCES departments (id)
        ON DELETE SET NULL,
    CONSTRAINT ck_finance_licences_seats CHECK (seats_paid IS NULL OR seats_paid >= 0),
    CONSTRAINT ck_finance_licences_cost CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT ck_finance_licences_last4 CHECK (card_last4 IS NULL OR card_last4 REGEXP '^[0-9]{4}$')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE finance_licence_assignments (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    licence_id  BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by  BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_licence_assignments (licence_id, user_id),
    KEY idx_finance_licence_assignments_user (user_id),
    CONSTRAINT fk_finance_licence_assignments_licence FOREIGN KEY (licence_id) REFERENCES finance_licences (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_finance_licence_assignments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE finance_exchange_rates (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    currency       VARCHAR(3)    NOT NULL,
    effective_from DATE          NOT NULL,
    -- AUD for one unit of the currency.
    rate_to_aud    DECIMAL(18,8) NOT NULL,
    source         VARCHAR(150)  NULL,
    version        INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by     BIGINT        NULL,
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by     BIGINT        NULL,
    deleted_at     DATETIME(6)   NULL,
    deleted_by     BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_finance_exchange_rates_currency_date (currency, effective_from),
    KEY idx_finance_exchange_rates_deleted (deleted_at),
    CONSTRAINT ck_finance_exchange_rates_rate CHECK (rate_to_aud > 0),
    CONSTRAINT ck_finance_exchange_rates_not_aud CHECK (currency <> 'AUD')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, name, module, description) VALUES
    ('FINANCE_EDIT', 'Edit Finance Tracker', 'FINANCE',
     'Change tools and licences, exchange rates, expenses and budgets (with FINANCE_VIEW)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p WHERE r.code = 'SUPER_ADMIN' AND p.code = 'FINANCE_EDIT';

INSERT INTO department_modules (module_key, department_id, enabled)
SELECT 'finance', d.id, TRUE FROM departments d WHERE d.code = 'HR';
