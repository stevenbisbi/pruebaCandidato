package com.conciliacion.pagos.adapters.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.conciliacion.pagos.adapters.inbound.rest.dto.BatchPayRequestDto;


@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {
    
    @PostMapping ("")
    public void receiveJson(@RequestBody BatchPayRequestDto request) {
    
        
}
    @PostMapping ("/file")
    public void receiveCsv(@RequestParam ("file") MultipartFile file,
                            @RequestParam (name = "id", required = false) String id) {
    }

    @GetMapping ("/{id}")
    public void getBatch(@PathVariable String id) {
    }

     @GetMapping ("/{id}/lines")
    public void getLines(@PathVariable String id,
                                  @RequestParam (name = "page", defaultValue = "0") Integer page,
                                  @RequestParam (name = "size", defaultValue = "100") Integer size) {
    }

     @GetMapping ("/{id}/download")
    public void downloadResult(@PathVariable String id) {
    }

}
