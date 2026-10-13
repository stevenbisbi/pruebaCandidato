package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.rest.dto.CursorPageResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.InvoiceCountResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.InvoiceDetailResponseDto;
import com.conciliacion.pagos.adapters.inbound.rest.dto.InvoiceResponseDto;
import com.conciliacion.pagos.application.usecase.GetInvoiceDetailUseCase;
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

/** Facturas de proveedores: consulta paginada con filtros (RF-23) y detalle con sus pagos. */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final SearchInvoicesUseCase searchInvoices;
    private final GetInvoiceDetailUseCase invoiceDetail;

    public InvoiceController(SearchInvoicesUseCase searchInvoices, GetInvoiceDetailUseCase invoiceDetail) {
        this.searchInvoices = searchInvoices;
        this.invoiceDetail = invoiceDetail;
    }

    /**
     * GET /api/v1/invoices: facturas ordenadas por numero, con filtros combinables (RF-23). La pagina
     * siguiente se pide con cursor; page salta directamente a una pagina (D-03).
     * Ejemplo: ?nit=900123&status=PARCIAL&status=PENDIENTE&dueFrom=2026-03-01&dueTo=2026-03-31
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
            @RequestParam(name = "size", defaultValue = "" + SearchInvoicesUseCase.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(name = "page", defaultValue = "1") int pageNumber) {
        InvoicePage page = searchInvoices.search(
            new InvoiceFilter(nit, statusSet(statuses), dueFrom, dueTo, minBalance, maxBalance, cursor, size, pageNumber));
        return new CursorPageResponseDto<>(
            page.items().stream().map(InvoiceResponseDto::from).toList(),
            page.items().size(), page.nextCursor(), page.nextCursor() != null);
    }

    /**
     * GET /api/v1/invoices/count: cuantas facturas cumplen los filtros, para numerar las paginas. Va
     * aparte de la consulta para que el frontend lo pida una vez por filtro y no en cada pagina.
     */
    @GetMapping("/count")
    public InvoiceCountResponseDto count(
            @RequestParam(name = "nit", required = false) String nit,
            @RequestParam(name = "status", required = false) List<InvoiceStatus> statuses,
            @RequestParam(name = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(name = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(name = "minBalance", required = false) BigDecimal minBalance,
            @RequestParam(name = "maxBalance", required = false) BigDecimal maxBalance) {
        return new InvoiceCountResponseDto(searchInvoices.count(
            new InvoiceFilter(nit, statusSet(statuses), dueFrom, dueTo, minBalance, maxBalance, null, 1, 1)));
    }

    /** GET /api/v1/invoices/{id}: una factura y todos los pagos que recibio (Anexo A). 404 si no existe. */
    @GetMapping("/{id}")
    public InvoiceDetailResponseDto getInvoiceById(@PathVariable String id) {
        return InvoiceDetailResponseDto.from(invoiceDetail.getDetail(id));
    }

    private static Set<InvoiceStatus> statusSet(List<InvoiceStatus> statuses) {
        return statuses == null || statuses.isEmpty() ? Set.of() : EnumSet.copyOf(statuses);
    }
}
