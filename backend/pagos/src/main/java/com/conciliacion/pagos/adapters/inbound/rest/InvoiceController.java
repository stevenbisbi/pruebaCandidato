package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.rest.dto.ApiResponses.InvoicePageResponse;
import com.conciliacion.pagos.adapters.inbound.rest.dto.ApiResponses.InvoiceResponse;
import com.conciliacion.pagos.application.usecase.SearchInvoicesUseCase;
import com.conciliacion.pagos.domain.model.InvoiceFilter;
import com.conciliacion.pagos.domain.model.InvoicePage;
import com.conciliacion.pagos.domain.model.InvoiceStatus;
import com.conciliacion.pagos.domain.model.InvoiceView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {

    private static final int MAX_PAGE_SIZE = 200;

    private final SearchInvoicesUseCase searchInvoices;

    public InvoiceController(SearchInvoicesUseCase searchInvoices) {
        this.searchInvoices = searchInvoices;
    }

    /**
     * GET /api/v1/facturas?nit=...&estado=PARCIAL&estado=PAGADA&cursor=FV-0001234
     *
     * @param cursor numero de la ultima factura de la pagina anterior (vacio en la primera pagina)
     */
    @GetMapping("/facturas")
    public InvoicePageResponse search(
        @RequestParam(name = "nit", required = false) String nit,
        @RequestParam(name = "estado", required = false) List<InvoiceStatus> statuses,
        @RequestParam(name = "cursor", required = false) String cursor,
        @RequestParam(name = "tamano", defaultValue = "50") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        InvoiceFilter filter = new InvoiceFilter(emptyToNull(nit), statuses, emptyToNull(cursor), safeSize);
        InvoicePage page = searchInvoices.search(filter);

        List<InvoiceResponse> items = new ArrayList<>();
        for (InvoiceView invoice : page.items()) {
            items.add(InvoiceResponse.of(invoice));
        }
        // El cursor de la pagina siguiente es el numero de la ultima factura de esta pagina.
        String nextCursor = null;
        if (page.hasNext()) {
            nextCursor = page.items().get(page.items().size() - 1).number();
        }
        return new InvoicePageResponse(items, nextCursor);
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
