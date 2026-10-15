package com.capstone.etp.funds;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/funds")
@RequiredArgsConstructor
public class FundsController {

    private final FundsService service;

    @PostMapping("/deposit")
    public ResponseEntity<FundsResponse> deposit(@PathVariable Long accountId, @Valid @RequestBody FundsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposit(accountId, request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<FundsResponse> withdraw(@PathVariable Long accountId, @Valid @RequestBody FundsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.withdraw(accountId, request));
    }

    @GetMapping
    public ResponseEntity<List<FundsResponse>> history(@PathVariable Long accountId,
                                                       @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(service.history(accountId, limit));
    }
}
