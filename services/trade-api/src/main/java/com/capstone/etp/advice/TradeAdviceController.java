package com.capstone.etp.advice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Advice for the signed-in customer. The account is the token's own account; an accountId may be given
 * only to pick between the token's own accounts (anything else is 403). Advice only: no order is ever placed here.
 */
@RestController
@RequestMapping("/api/v1/trade-advice")
public class TradeAdviceController {

    private final TradeAdviceService service;

    TradeAdviceController(TradeAdviceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TradeAdvice>> advice(@RequestParam(required = false) Long accountId) {
        return ResponseEntity.ok(service.adviceFor(accountId));
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<TradeAdvice> adviceForSymbol(@PathVariable String symbol,
                                                       @RequestParam(required = false) Long accountId) {
        return ResponseEntity.ok(service.adviceFor(accountId, symbol));
    }
}
