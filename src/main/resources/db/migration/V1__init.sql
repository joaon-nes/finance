-- Schema inicial do app

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER'
                  CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id            UUID        PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES users (id),
    name          VARCHAR(80) NOT NULL,
    type          VARCHAR(20) NOT NULL
                  CHECK (type IN ('CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH')),
    balance_cents BIGINT      NOT NULL DEFAULT 0,
    version       BIGINT      NOT NULL DEFAULT 0,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

CREATE TABLE categories (
    id        UUID        PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES users (id),
    parent_id UUID        REFERENCES categories (id),
    name      VARCHAR(80) NOT NULL,
    kind      VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE')),
    UNIQUE NULLS NOT DISTINCT (user_id, parent_id, name)
);

CREATE TABLE import_batches (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id),
    account_id     UUID         NOT NULL REFERENCES accounts (id),
    file_name      VARCHAR(200) NOT NULL,
    format         VARCHAR(10)  NOT NULL CHECK (format IN ('CSV', 'OFX')),
    status         VARCHAR(15)  NOT NULL
                   CHECK (status IN ('PENDING', 'DONE', 'FAILED')),
    total_rows     INT          NOT NULL DEFAULT 0,
    imported_rows  INT          NOT NULL DEFAULT 0,
    duplicate_rows INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE transactions (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id),
    type            VARCHAR(10)  NOT NULL
                    CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    description     VARCHAR(200) NOT NULL,
    occurred_on     DATE         NOT NULL,
    category_id     UUID         REFERENCES categories (id),
    idempotency_key VARCHAR(80),
    import_batch_id UUID         REFERENCES import_batches (id),
    reverses_id     UUID         REFERENCES transactions (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id             UUID        PRIMARY KEY,
    transaction_id UUID        NOT NULL REFERENCES transactions (id),
    account_id     UUID        NOT NULL REFERENCES accounts (id),
    amount_cents   BIGINT      NOT NULL CHECK (amount_cents <> 0),
    external_hash  VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (account_id, external_hash)
);

CREATE TABLE budgets (
    id          UUID   PRIMARY KEY,
    user_id     UUID   NOT NULL REFERENCES users (id),
    category_id UUID   NOT NULL REFERENCES categories (id),
    month       DATE   NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    limit_cents BIGINT NOT NULL CHECK (limit_cents > 0),
    UNIQUE (user_id, category_id, month)
);

CREATE TABLE recurring_rules (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id),
    account_id   UUID         NOT NULL REFERENCES accounts (id),
    category_id  UUID         REFERENCES categories (id),
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount_cents BIGINT       NOT NULL CHECK (amount_cents > 0),
    description  VARCHAR(200) NOT NULL,
    frequency    VARCHAR(10)  NOT NULL
                 CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    next_run_on  DATE         NOT NULL,
    end_on       DATE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE audit_log (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    UUID,
    entity     VARCHAR(40) NOT NULL,
    entity_id  UUID,
    action     VARCHAR(20) NOT NULL,
    payload    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tx_user_date  ON transactions (user_id, occurred_on DESC);
CREATE INDEX idx_le_account    ON ledger_entries (account_id, created_at DESC);
CREATE INDEX idx_le_tx         ON ledger_entries (transaction_id);
CREATE INDEX idx_recurring_due ON recurring_rules (next_run_on) WHERE active;

CREATE VIEW v_balance_reconciliation AS
SELECT a.id              AS account_id,
       a.balance_cents   AS cached_balance_cents,
       COALESCE(SUM(le.amount_cents), 0) AS ledger_balance_cents
FROM accounts a
LEFT JOIN ledger_entries le ON le.account_id = a.id
GROUP BY a.id, a.balance_cents
HAVING a.balance_cents <> COALESCE(SUM(le.amount_cents), 0);