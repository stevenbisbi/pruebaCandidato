-- Carga de los datos de prueba (seccion 7). El archivo se lee del lado del servidor: el
-- docker-compose monta seed/out en /seed dentro del contenedor de PostgreSQL.
-- Solo se aplica cuando la ubicacion db/seed esta habilitada (perfil por defecto); las pruebas
-- de integracion la excluyen.
CREATE TEMP TABLE staging_invoice (
    number       VARCHAR(30),
    nit          VARCHAR(20),
    name         VARCHAR(200),
    issue_date   DATE,
    due_date     DATE,
    total_amount NUMERIC(18, 2)
) ON COMMIT DROP;

COPY staging_invoice FROM '${seed_dir}/facturas.csv' WITH (FORMAT csv, DELIMITER ';', HEADER true);

INSERT INTO supplier (nit, name)
SELECT DISTINCT ON (nit) nit, name
FROM staging_invoice
ORDER BY nit, number;

-- RF-02: el saldo inicial es el valor total.
INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
SELECT number, nit, issue_date, due_date, total_amount, total_amount, 'PENDIENTE'
FROM staging_invoice;
