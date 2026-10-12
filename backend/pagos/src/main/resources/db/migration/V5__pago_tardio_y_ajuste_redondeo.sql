-- A-04: la factura recuerda si recibio algun pago despues de su vencimiento.
-- DEFAULT false: las 500.000 facturas existentes no tienen pagos, asi que ninguna fue tardia.
ALTER TABLE invoice ADD COLUMN had_late_payment BOOLEAN NOT NULL DEFAULT false;

-- A-02: ajuste por redondeo al saldar la factura (positivo si se condona, negativo si se pago de mas).
-- Nulo en los rechazos y cuando no hubo ajuste.
ALTER TABLE payment_line_result ADD COLUMN rounding_adjustment NUMERIC(18, 2);