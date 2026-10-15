package com.capstone.etp.candles;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Market data, not account data: any signed-in customer may read it. */
@RestController
@RequestMapping("/api/v1/quotes/candles")
@RequiredArgsConstructor
public class CandleController {

    private final CandleService service;

    @GetMapping("/{symbol}")
    public ResponseEntity<CandlesResponse> candles(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String period,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(service.candles(symbol, period, limit));
    }
}
