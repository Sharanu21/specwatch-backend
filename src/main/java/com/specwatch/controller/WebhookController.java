package com.specwatch.controller;

import com.specwatch.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestParam(value = "token", defaultValue = "") String token,
            @RequestBody String payload
    ) {
        log.info("Received GitHub webhook event: {}", event);

        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            // Update: Pass token to service for per-project validation
            webhookService.handlePushEvent(payload, token);
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("Webhook received");
    }
}