package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.rest.dto.CursorPageResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.InvoiceResponseDto;
import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Facturas de proveedores: consulta paginada con filtros (RF-23). */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final SearchInvoicesUseCase searchInvoices;

    public InvoiceController(SearchInvoicesUseCase searchInvoices) {
        this.searchInvoices = searchInvoices;
    }

    /**
     * GET /api/v1/invoices: facturas ordenadas por numero, con filtros combinables y paginacion por
     * cursor (D-03). Ejemplo: ?nit=900123&status=PARCIAL&status=PENDIENTE&dueFrom=2026-03-01&dueTo=2026-03-31
     */
    @GetMapping
    public CursorPageResponseDto<InvoiceResponseDto> search(
            @RequestParam(name = "nit", required = false) String nit,
            @RequestParam(name = "status", required = false) List<InvoiceStatus> statuses,
            @RequestParam(name = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(name = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(name = "minBalance", required = false) BigDecimal minBalance,
            @RequestParam(name = "maxBalance", required = false) BigDecimal maxBalance,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "" + SearchInvoicesUseCase.DEFAULT_PAGE_SIZE) int size) {
        Set<InvoiceStatus> statusSet = statuses == null || statuses.isEmpty()
            ? Set.of()
            : EnumSet.copyOf(statuses);
        InvoicePage page = searchInvoices.search(
            new InvoiceFilter(nit, statusSet, dueFrom, dueTo, minBalance, maxBalance, cursor, size));
        return new CursorPageResponseDto<>(
            page.items().stream().map(InvoiceResponseDto::from).toList(),
            page.items().size(), page.nextCursor(), page.nextCursor() != null);
    }

    /** GET /api/v1/invoices/{id}: detalle de una factura y sus pagos (Anexo A). Pendiente. */
    @GetMapping("/{id}")
    public void getInvoiceById(@PathVariable String id) {
        // Se implementa en su propio commit.
    }
}
