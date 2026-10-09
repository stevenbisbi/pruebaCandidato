-- Lotes de pago recibidos (RF-16). El id es la llave de idempotencia de RF-20.
CREATE TABLE payment_batch (
    id              VARCHAR(80)    PRIMARY KEY,
    source          VARCHAR(40)    NOT NULL,
    channel         VARCHAR(10)    NOT NULL CHECK (channel IN ('JSON', 'CSV')),
    generated_on    DATE,
    content_hash    CHAR(64)       NOT NULL,
    received_at     TIMESTAMPTZ    NOT NULL,
    total_lines     INT,
    applied_lines   INT,
    rejected_lines  INT,
    applied_amount  NUMERIC(18, 2),
    rejected_amount NUMERIC(18, 2)
);

-- Resultado linea por linea (RF-21) y auditoria de cada aplicacion o rechazo (RF-15).
-- Una fila con outcome = 'APLICADO' es un pago aplicado: no hay otra tabla de pagos.
CREATE TABLE payment_line_result (
    batch_id       VARCHAR(80)    NOT NULL REFERENCES payment_batch (id),
    line_number    INT            NOT NULL,
    reference      VARCHAR(80),
    invoice_number VARCHAR(30),
    amount         NUMERIC(18, 2),
    paid_at        TIMESTAMPTZ,
    outcome        VARCHAR(10)    NOT NULL CHECK (outcome IN ('APLICADO', 'RECHAZADO')),
    reason         VARCHAR(30),
    detail         VARCHAR(300),
    balance_before NUMERIC(18, 2),
    balance_after  NUMERIC(18, 2),
    status_after   VARCHAR(25),
    processed_by   VARCHAR(60)    NOT NULL,
    processed_at   TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (batch_id, line_number),
    CONSTRAINT line_amount_whole_pesos CHECK (amount IS NULL OR amount = trunc(amount))
);

-- RF-12 garantizado por la base, no solo por el codigo: una referencia se aplica una sola vez.
CREATE UNIQUE INDEX ux_line_applied_reference ON payment_line_result (reference) WHERE outcome = 'APLICADO';

-- Indices de la consulta de facturas (RF-23, PR-01). La paginacion avanza por numero de factura,
-- que nunca cambia (PR-03): la llave primaria cubre el caso sin filtros.
CREATE INDEX ix_invoice_nit_number ON invoice (supplier_nit, number);
CREATE INDEX ix_invoice_status_number ON invoice (status, number);

ANALYZE invoice;
