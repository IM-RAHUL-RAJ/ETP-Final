package com.capstone.etp.alerts;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/accounts/{accountId}/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> list(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.list(accountId));
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(
            @PathVariable Long accountId,
            @Valid @RequestBody CreateAlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(accountId, request));
    }

    @PutMapping("/{alertId}")
    public ResponseEntity<AlertResponse> update(
            @PathVariable Long accountId,
            @PathVariable Long alertId,
            @Valid @RequestBody UpdateAlertRequest request) {
        return ResponseEntity.ok(service.update(accountId, alertId, request));
    }

    @PutMapping("/{alertId}/enabled")
    public ResponseEntity<Map<String, Boolean>> setEnabled(
            @PathVariable Long accountId,
            @PathVariable Long alertId,
            @Valid @RequestBody EnabledRequest request) {
        AlertResponse updated = service.setEnabled(accountId, alertId, request.enabled());
        return ResponseEntity.ok(Map.of("enabled", updated.enabled()));
    }

    @GetMapping("/{alertId}/triggers")
    public ResponseEntity<AlertTriggersResponse> triggers(
            @PathVariable Long accountId,
            @PathVariable Long alertId) {
        return ResponseEntity.ok(service.triggers(accountId, alertId));
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<AlertResponse> remove(
            @PathVariable Long accountId,
            @PathVariable Long alertId) {
        return ResponseEntity.ok(service.remove(accountId, alertId));
    }
}
