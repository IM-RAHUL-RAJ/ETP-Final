package com.capstone.etp.preferences;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//Exposes customer preference endpoints for reading and updating notification preferences.
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private final PreferencesService service;

    @GetMapping
    public ResponseEntity<PreferencesResponse> get(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.get(accountId));
    }

    @PutMapping
    public ResponseEntity<PreferencesResponse> put(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(service.update(accountId, request));
    }
}