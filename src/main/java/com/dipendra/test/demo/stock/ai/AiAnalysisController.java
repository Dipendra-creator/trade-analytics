package com.dipendra.test.demo.stock.ai;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-analysis")
public class AiAnalysisController {
    private final AiTradeAnalysisService analysis;

    public AiAnalysisController(AiTradeAnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping
    public ResponseEntity<?> latest() {
        return analysis.latest()
                .<ResponseEntity<?>>map(snapshot -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore()).body(snapshot))
                .orElseGet(() -> ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                        .body(new ErrorResponse("Analytics are warming up")));
    }

    private record ErrorResponse(String message) { }
}
