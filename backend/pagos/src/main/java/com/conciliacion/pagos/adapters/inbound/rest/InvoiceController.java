package com.conciliacion.pagos.adapters.inbound.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController 
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    @GetMapping ("")
    public void getInvoices() {
    }

    @GetMapping ("/{id}")
    public void getInvoiceById(@PathVariable String id) {
    }
    
}
