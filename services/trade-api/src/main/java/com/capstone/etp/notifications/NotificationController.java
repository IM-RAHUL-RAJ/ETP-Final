package com.capstone.etp.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/accounts/{accountId}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(service.list(accountId, unreadOnly, limit, type));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> counts(@PathVariable Long accountId) {
        return ResponseEntity.ok(service.counts(accountId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> unreadCount(@PathVariable Long accountId) {
        return ResponseEntity.ok(Map.of("unread", service.unreadCount(accountId)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(@PathVariable Long accountId) {
        return ResponseEntity.ok(Map.of("updated", service.markAllRead(accountId)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long accountId,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(service.markRead(accountId, notificationId));
    }
}
