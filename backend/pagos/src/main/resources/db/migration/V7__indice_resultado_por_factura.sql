-- Detalle de una factura con sus pagos (Anexo A) y tablero (RF-24): las dos consultas buscan las
-- lineas de resultado por numero de factura. Sin este indice recorrerian todo el historial de
-- pagos, que crece con cada lote (50.000 lineas por cada carga grande).
CREATE INDEX ix_line_result_invoice ON payment_line_result (invoice_number);
