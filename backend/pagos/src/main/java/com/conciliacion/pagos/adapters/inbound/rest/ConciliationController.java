package com.conciliacion.pagos.adapters.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping ("/api/v1/conciliation")
public class ConciliationController {
    
    @GetMapping ("")
    public void getConciliation() {
    }
}
