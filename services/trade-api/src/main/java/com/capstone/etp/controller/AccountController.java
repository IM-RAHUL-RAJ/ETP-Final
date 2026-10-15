package com.capstone.etp.controller;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.service.AccountService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        System.out.println("Fetching account with ID: " + id);
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getAccount(id));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getBalance(id));
    }

    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionResponse>> getPositions(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getPositions(id));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderHistoryEntry>> getOrders(
            @PathVariable Long id,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getOrders(id, status, from, to));
    }

    @GetMapping("/{id}/watchlist")
    public ResponseEntity<List<WatchlistEntry>> getWatchlist(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getWatchlist(id));
    }

    @PutMapping("/{id}/watchlist/{symbol}")
    public ResponseEntity<List<WatchlistEntry>> addToWatchlist(
            @PathVariable Long id,
            @PathVariable String symbol) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.addToWatchlist(id, symbol));
    }

    @DeleteMapping("/{id}/watchlist/{symbol}")
    public ResponseEntity<List<WatchlistEntry>> removeFromWatchlist(
            @PathVariable Long id,
            @PathVariable String symbol) {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.removeFromWatchlist(id, symbol));
    }
}
