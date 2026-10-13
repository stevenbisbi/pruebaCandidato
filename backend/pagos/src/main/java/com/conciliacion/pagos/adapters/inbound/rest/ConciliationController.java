package com.conciliacion.pagos.adapters.inbound.rest;

import com.conciliacion.pagos.adapters.inbound.rest.dto.ConciliationResponseDto;
import com.conciliacion.pagos.application.usecase.GetConciliationStateUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Tablero de conciliacion (RF-24). */
@RestController
@RequestMapping("/api/v1/conciliation")
public class ConciliationController {

    private final GetConciliationStateUseCase conciliation;

    public ConciliationController(GetConciliationStateUseCase conciliation) {
        this.conciliation = conciliation;
    }

    /**
     * GET /api/v1/conciliation: totales y top 10 de proveedores sobre las facturas con vencimiento en
     * el rango (A-08). Es el mismo filtro de vencimiento de la consulta de facturas (RF-23); sin
     * rango, el tablero cubre todas las facturas.
     */
    @GetMapping
    public ConciliationResponseDto getDashboard(
            @RequestParam(name = "dueFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(name = "dueTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) {
        return ConciliationResponseDto.from(conciliation.getDashboard(dueFrom, dueTo));
    }
}
