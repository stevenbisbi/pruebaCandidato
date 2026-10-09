CREATE TABLE supplier (
    nit  VARCHAR(20)  PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE invoice (
    number       VARCHAR(30)    PRIMARY KEY,
    supplier_nit VARCHAR(20)    NOT NULL REFERENCES supplier (nit),
    issue_date   DATE           NOT NULL,
    due_date     DATE           NOT NULL,
    total_amount NUMERIC(18, 2) NOT NULL CHECK (total_amount > 0),
    balance      NUMERIC(18, 2) NOT NULL,
    status       VARCHAR(25)    NOT NULL,
    CONSTRAINT invoice_balance_range CHECK (balance >= 0 AND balance <= total_amount),
    CONSTRAINT invoice_status_valid CHECK (status IN ('PENDIENTE', 'PARCIAL', 'PAGADA', 'PAGADA_EXTEMPORANEA'))
);