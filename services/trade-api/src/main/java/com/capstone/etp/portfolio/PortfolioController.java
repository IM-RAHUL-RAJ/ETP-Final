package com.capstone.etp.portfolio;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolio/{accountId}")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService service;

    @GetMapping
    public ResponseEntity<PortfolioSummaryResponse> summary(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.summary(accountId));
    }

    @GetMapping("/positions")
    public ResponseEntity<List<PricedPositionResponse>> positions(
            @PathVariable Long accountId,
            @RequestParam(required = false) String symbol) {
        return ResponseEntity.ok(service.positions(accountId, symbol));
    }

    @GetMapping("/pnl")
    public ResponseEntity<PnlResponse> pnl(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean bySymbol) {
        return ResponseEntity.ok(service.pnl(accountId, from, to, bySymbol));
    }
}
