package com.dipendra.test.demo.reliability;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reliability")
public class ReliabilityController {
    private final ReliabilityService service;

    public ReliabilityController(ReliabilityService service) { this.service = service; }

    @GetMapping("/summary")
    public ResponseEntity<ReliabilityService.ReliabilitySummary> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary());
    }
}
