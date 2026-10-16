-- V36: Vendor Management (Expansion Phase X7), as the "vendors" department tool (X5 module framework).
--
-- 1. vendors: name (unique, including vendors in the recycle bin), ABN / GST / tax ID, category, country, address,
--    website, payment terms, status (ACTIVE, INACTIVE, BLACKLISTED; set by people), rating (1-5) and notes.
-- 2. vendor_contacts: a vendor's contacts (name, role, email, phone), in order. Children of the vendor: they stay
--    untouched while it is in the recycle bin and go with its permanent delete.
-- 3. vendor_contracts: title (unique per vendor), start and end date, value and currency, renewal date, auto-renew,
--    notice period and notes. Records people delete (recycle bin). Never stored, always computed: whether a contract is
--    upcoming, active or expired, the date it comes up for renewal (the renewal date, or else the end date), the last
--    day to give notice, and the renewal reminders 30 and 7 days before.
-- 4. vendor_contract_documents: files on a contract (through files / FileStorageService). They stay while the
--    contract is in the bin and are removed, file and all, with its permanent delete.
-- 5. VENDOR_VIEW and VENDOR_EDIT, on SUPER_ADMIN only (grantable later from Admin settings), and the module switched on
--    for HR where that department exists.
--
-- Licences and expenses (Phase X8) will point at vendors.id; the vendor page lists them through VendorLinkSource.

CREATE TABLE vendors (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(200) NOT NULL,
    -- ABN, GST or another tax ID, as written by the vendor.
    tax_id        VARCHAR(50)  NULL,
    -- SOFTWARE, HARDWARE, RECRUITMENT_AGENCY, MARKETING, SERVICES or OTHER.
    category      VARCHAR(32)  NOT NULL DEFAULT 'OTHER',
    country       VARCHAR(100) NULL,
    address       VARCHAR(500) NULL,
    website       VARCHAR(300) NULL,
    payment_terms VARCHAR(100) NULL,
    -- ACTIVE, INACTIVE or BLACKLISTED; set by people, not derived.
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    rating        INT          NULL,
    notes         TEXT         NULL,
    version       INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by    BIGINT       NULL,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT       NULL,
    deleted_at    DATETIME(6)  NULL,
    deleted_by    BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendors_name (name),
    KEY idx_vendors_status (status),
    KEY idx_vendors_category (category),
    KEY idx_vendors_country (country),
    KEY idx_vendors_deleted (deleted_at),
    CONSTRAINT ck_vendors_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE vendor_contacts (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    vendor_id  BIGINT       NOT NULL,
    name       VARCHAR(150) NOT NULL,
    role       VARCHAR(100) NULL,
    email      VARCHAR(254) NULL,
    phone      VARCHAR(50)  NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by BIGINT       NULL,
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_vendor_contacts_vendor (vendor_id),
    KEY idx_vendor_contacts_email (email),
    CONSTRAINT fk_vendor_contacts_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE vendor_contracts (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    vendor_id          BIGINT        NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    start_date         DATE          NOT NULL,
    -- Open-ended when NULL.
    end_date           DATE          NULL,
    value              DECIMAL(14,2) NULL,
    currency           VARCHAR(3)    NOT NULL DEFAULT 'INR',
    renewal_date       DATE          NULL,
    auto_renew         BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Days of notice the vendor needs before the renewal date to cancel or change the contract.
    notice_period_days INT           NULL,
    notes              TEXT          NULL,
    version            INT           NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by         BIGINT        NULL,
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by         BIGINT        NULL,
    deleted_at         DATETIME(6)   NULL,
    deleted_by         BIGINT        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendor_contracts_vendor_title (vendor_id, title),
    KEY idx_vendor_contracts_dates (start_date, end_date),
    KEY idx_vendor_contracts_renewal (renewal_date),
    KEY idx_vendor_contracts_end (end_date),
    KEY idx_vendor_contracts_deleted (deleted_at),
    CONSTRAINT fk_vendor_contracts_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id),
    CONSTRAINT ck_vendor_contracts_value CHECK (value IS NULL OR value >= 0),
    CONSTRAINT ck_vendor_contracts_notice CHECK (notice_period_days IS NULL OR notice_period_days BETWEEN 0 AND 730)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE vendor_contract_documents (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    contract_id BIGINT      NOT NULL,
    file_id     BIGINT      NOT NULL,
    added_by    BIGINT      NULL,
    added_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by  BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendor_contract_documents_file (file_id),
    KEY idx_vendor_contract_documents_contract (contract_id),
    KEY idx_vendor_contract_documents_added_by (added_by),
    CONSTRAINT fk_vendor_contract_documents_contract FOREIGN KEY (contract_id) REFERENCES vendor_contracts (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_vendor_contract_documents_file FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE,
    CONSTRAINT fk_vendor_contract_documents_added_by FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO permissions (code, name, module, description) VALUES
    ('VENDOR_VIEW', 'View Vendors', 'VENDORS', 'Open Vendor Management: vendors, contacts, contracts and documents'),
    ('VENDOR_EDIT', 'Edit Vendors', 'VENDORS', 'Change vendors, contacts, contracts and contract documents');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE r.code = 'SUPER_ADMIN' AND p.code IN ('VENDOR_VIEW', 'VENDOR_EDIT');

INSERT INTO department_modules (module_key, department_id, enabled)
SELECT 'vendors', d.id, TRUE FROM departments d WHERE d.code = 'HR';
