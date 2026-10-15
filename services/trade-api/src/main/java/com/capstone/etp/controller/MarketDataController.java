package com.capstone.etp.controller;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    @GetMapping("/{id}/watchlist/quotes")
    public ResponseEntity<List<MarketQuote>> getWatchlistQuotes(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(marketDataService.getWatchlistQuotes(id));
    }
}
