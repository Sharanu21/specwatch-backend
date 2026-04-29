package com.specwatch.controller;

import com.specwatch.service.GitHubService;
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
    private final GitHubService gitHubService;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", defaultValue = "") String signature,
            @RequestBody String payload
    ) {
        log.info("Received GitHub webhook event: {}", event);

        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (!gitHubService.isValidSignature(payload, signature, webhookSecret)) {
                log.warn("Invalid webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
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