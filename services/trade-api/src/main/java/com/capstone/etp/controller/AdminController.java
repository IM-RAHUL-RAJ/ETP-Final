package com.capstone.etp.controller;

import com.capstone.etp.dto.AdminAccountSummary;
import com.capstone.etp.dto.RegistrationRequest;
import com.capstone.etp.dto.RegistrationResponse;
import com.capstone.etp.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final RegistrationService registrationService;

    @GetMapping("/accounts")
    public ResponseEntity<List<AdminAccountSummary>> listAccounts() {
        return ResponseEntity.status(HttpStatus.OK).body(registrationService.listAccounts());
    }

    /** Opens the client and account. The login is created next, by the browser, at the auth service. */
    @PostMapping("/registrations")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    /** Takes back a registration whose login could not be created. Only for a brand-new, unused account. */
    @DeleteMapping("/registrations/{accountId}")
    public ResponseEntity<Void> undo(@PathVariable Long accountId) {
        registrationService.undo(accountId);
        return ResponseEntity.noContent().build();
    }
}
