package com.conciliacion.pagos.infrastructure.web;

import com.conciliacion.pagos.application.query.InvoiceFilter;
import com.conciliacion.pagos.application.query.InvoicePage;
import com.conciliacion.pagos.application.query.InvoiceView;
import com.conciliacion.pagos.application.service.DashboardService;
import com.conciliacion.pagos.application.service.InvoiceQueryService;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.DashboardResponse;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.InvoiceDetailResponse;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.InvoicePageResponse;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.InvoiceResponse;
import com.conciliacion.pagos.infrastructure.web.dto.ApiResponses.LineResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final InvoiceQueryService invoices;
    private final DashboardService dashboard;

    public InvoiceController(InvoiceQueryService invoices, DashboardService dashboard) {
        this.invoices = invoices;
        this.dashboard = dashboard;
    }

    @GetMapping("/facturas")
    public InvoicePageResponse search(
        @RequestParam(name = "nit", required = false) String nit,
        @RequestParam(name = "estado", required = false) List<InvoiceStatus> statuses,
        @RequestParam(name = "venceDesde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
        @RequestParam(name = "venceHasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
        @RequestParam(name = "saldoMin", required = false) BigDecimal balanceMin,
        @RequestParam(name = "saldoMax", required = false) BigDecimal balanceMax,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "tamano", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {

        Cursor after = Cursor.decode(cursor);
        Set<InvoiceStatus> statusSet = statuses == null || statuses.isEmpty() ? null : EnumSet.copyOf(statuses);
        int boundedSize = Math.clamp(size, 1, InvoiceQueryService.MAX_PAGE_SIZE);
        InvoicePage page = invoices.search(new InvoiceFilter(blankToNull(nit), statusSet, dueFrom, dueTo, balanceMin,
            balanceMax, after == null ? null : after.dueDate(), after == null ? null : after.number(), boundedSize));

        String next = null;
        if (page.hasNext()) {
            InvoiceView last = page.items().getLast();
            next = new Cursor(last.dueDate(), last.number()).encode();
        }
        return new InvoicePageResponse(page.items().stream().map(InvoiceResponse::of).toList(), next);
    }

    @GetMapping("/facturas/{numero}")
    public InvoiceDetailResponse detail(@PathVariable("numero") String number) {
        InvoiceQueryService.InvoiceDetail detail = invoices.detail(number);
        return new InvoiceDetailResponse(InvoiceResponse.of(detail.invoice()),
            detail.payments().stream().map(LineResponse::of).toList());
    }

    @GetMapping("/conciliacion/tablero")
    public DashboardResponse dashboard(
        @RequestParam(name = "desde", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return DashboardResponse.of(dashboard.load(from, to));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Cursor opaco para el cliente: base64url de "fecha|numero". */
    record Cursor(LocalDate dueDate, String number) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((dueDate + "|" + number).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
                int sep = raw.indexOf('|');
                return new Cursor(LocalDate.parse(raw.substring(0, sep)), raw.substring(sep + 1));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Cursor de paginacion invalido");
            }
        }
    }
}
