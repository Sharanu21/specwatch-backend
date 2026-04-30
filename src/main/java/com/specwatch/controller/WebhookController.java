package com.specwatch.controller;

import com.specwatch.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @Value("${github.webhook.secret:}")
    private String webhookSecret;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestParam(value = "token", defaultValue = "") String token,
            @RequestBody String payload
    ) {
        log.info("Received GitHub webhook event: {}", event);

        // Secure via URL token instead of HMAC signature
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!webhookSecret.equals(token)) {
                log.warn("Invalid webhook token — rejecting request");
                return ResponseEntity.status(401).body("Unauthorized");
            }
        }

        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            webhookService.handlePushEvent(payload);
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok("Webhook received");
    }
}