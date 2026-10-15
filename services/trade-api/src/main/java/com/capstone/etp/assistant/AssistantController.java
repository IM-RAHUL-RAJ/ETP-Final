package com.capstone.etp.assistant;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The in-app companion. The model is called from here on the server; the browser never sees a key. */
@RestController
@RequestMapping("/api/v1/accounts/{accountId}/assistant")
public class AssistantController {

    private final AssistantService service;

    AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@PathVariable Long accountId, @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(service.chat(accountId, request));
    }

    @GetMapping("/quick-actions")
    public ResponseEntity<List<QuickAction>> quickActions(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.quickActions(accountId));
    }
}
