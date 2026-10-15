package com.capstone.etp.controller;

import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<InstrumentResponse>> getInstruments() {
        return ResponseEntity.status(HttpStatus.OK).body(accountService.getInstruments());
    }
}
