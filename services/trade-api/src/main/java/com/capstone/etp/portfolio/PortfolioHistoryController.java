package com.capstone.etp.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/portfolio/{accountId}/history")
@RequiredArgsConstructor
public class PortfolioHistoryController {

    private final PortfolioHistoryService service;

    @GetMapping
    public ResponseEntity<PortfolioHistoryResponse> history(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(service.history(accountId, range));
    }
}
