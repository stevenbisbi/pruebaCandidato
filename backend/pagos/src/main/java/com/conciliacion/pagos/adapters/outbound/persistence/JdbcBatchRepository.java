package com.conciliacion.pagos.adapters.outbound.persistence;

import com.conciliacion.pagos.domain.model.Batch;
import com.conciliacion.pagos.domain.repository.BatchRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Implementacion con JDBC del puerto BatchRepository. */
@Repository
public class JdbcBatchRepository implements BatchRepository {

    private final JdbcTemplate jdbc;

    public JdbcBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ON CONFLICT DO NOTHING: si el id ya existe no inserta nada y devuelve 0 filas.
     * Si otra transaccion esta insertando el mismo id, PostgreSQL hace esperar a esta hasta que
     * aquella termine; por eso dos reenvios simultaneos no se procesan dos veces.
     */
    @Override
    public boolean insert(String id, String source, String contentHash, Instant receivedAt) {
        int insertedRows = jdbc.update("""
                INSERT INTO payment_batch (id, source, channel, content_hash, received_at)
                VALUES (?, ?, 'CSV', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
            id, source, contentHash, Timestamp.from(receivedAt));
        return insertedRows == 1;
    }

    @Override
    public Batch find(String id) {
        List<Batch> rows = jdbc.query("SELECT * FROM payment_batch WHERE id = ?", (rs, rowNum) -> toBatch(rs), id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    @Override
    public void saveTotals(Batch batch) {
        jdbc.update("""
                UPDATE payment_batch
                SET total_lines = ?, applied_lines = ?, rejected_lines = ?, applied_amount = ?, rejected_amount = ?
                WHERE id = ?
                """,
            batch.totalLines(), batch.appliedLines(), batch.rejectedLines(), batch.appliedAmount(),
            batch.rejectedAmount(), batch.id());
    }

    private Batch toBatch(ResultSet rs) throws SQLException {
        return new Batch(
            rs.getString("id"),
            rs.getString("source"),
            rs.getString("content_hash"),
            rs.getTimestamp("received_at").toInstant(),
            rs.getInt("total_lines"),
            rs.getInt("applied_lines"),
            rs.getInt("rejected_lines"),
            rs.getBigDecimal("applied_amount"),
            rs.getBigDecimal("rejected_amount"));
    }
}
