package com.conciliacion.pagos.integration;

import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.InvoiceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consulta de facturas contra PostgreSQL real (RF-23, D-03): el salto directo a una pagina debe dar
 * exactamente las mismas facturas que llegar a ella pagina por pagina con el cursor.
 */
@Testcontainers
@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration")
class InvoiceSearchIntegrationTest {

    private static final int INVOICES = 23;
    private static final int SIZE = 5;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    SearchInvoicesUseCase searchInvoices;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE payment_line_result, payment_batch, invoice, supplier CASCADE");
        jdbc.update("INSERT INTO supplier (nit, name) VALUES ('900-1', 'Proveedor Uno')");
        // 23 facturas de 5 en 5: 5 paginas, la ultima con 3. Las pares estan PARCIAL.
        for (int i = 1; i <= INVOICES; i++) {
            jdbc.update("""
                INSERT INTO invoice (number, supplier_nit, issue_date, due_date, total_amount, balance, status)
                VALUES (?, '900-1', ?, ?, 100, ?, ?)
                """, "FV-%04d".formatted(i), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1),
                new BigDecimal(i % 2 == 0 ? "60" : "100"), i % 2 == 0 ? "PARCIAL" : "PENDIENTE");
        }
    }

    @Test
    void jumpingToAnyPageGivesTheSameInvoicesAsWalkingWithTheCursor() {
        List<List<String>> walked = walkWithCursor();
        assertThat(walked).hasSize(5);

        for (int page = 1; page <= walked.size(); page++) {
            // Las paginas 2 y 3 se posicionan desde el inicio; la 4 y la 5, leyendo al reves desde el final.
            assertThat(numbers(searchInvoices.search(filter(Set.of(), null, page))))
                .as("pagina %d", page)
                .isEqualTo(walked.get(page - 1));
        }
    }

    @Test
    void theLastPageHasTheRemainderAndNoNextPage() {
        InvoicePage last = searchInvoices.search(filter(Set.of(), null, 5));

        assertThat(numbers(last)).containsExactly("FV-0021", "FV-0022", "FV-0023");
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    void afterAJumpTheNextPageContinuesWithTheCursor() {
        InvoicePage second = searchInvoices.search(filter(Set.of(), null, 2));

        InvoicePage third = searchInvoices.search(filter(Set.of(), second.nextCursor(), 1));

        assertThat(numbers(third)).containsExactly("FV-0011", "FV-0012", "FV-0013", "FV-0014", "FV-0015");
    }

    @Test
    void aPageBeyondTheEndReturnsTheLastOne() {
        assertThat(numbers(searchInvoices.search(filter(Set.of(), null, 99))))
            .containsExactly("FV-0021", "FV-0022", "FV-0023");
    }

    @Test
    void countsAndJumpsWithinTheFilteredResult() {
        Set<InvoiceStatus> partial = Set.of(InvoiceStatus.PARCIAL);

        assertThat(searchInvoices.count(filter(partial, null, 1))).isEqualTo(11);
        // 11 parciales de 5 en 5: la tercera pagina es la ultima, con una sola factura.
        assertThat(numbers(searchInvoices.search(filter(partial, null, 3)))).containsExactly("FV-0022");
    }

    private List<List<String>> walkWithCursor() {
        List<List<String>> pages = new ArrayList<>();
        String cursor = null;
        do {
            InvoicePage page = searchInvoices.search(filter(Set.of(), cursor, 1));
            pages.add(numbers(page));
            cursor = page.nextCursor();
        } while (cursor != null);
        return pages;
    }

    private static InvoiceFilter filter(Set<InvoiceStatus> statuses, String cursor, int page) {
        return new InvoiceFilter(null, statuses, null, null, null, null, cursor, SIZE, page);
    }

    private static List<String> numbers(InvoicePage page) {
        return page.items().stream().map(InvoiceView::number).toList();
    }
}
