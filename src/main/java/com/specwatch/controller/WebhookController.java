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
        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            webhookService.handlePushEvent(payload);
            return ResponseEntity.ok("SUCCESS: Webhook processed and saved to database.");
        } catch (Exception e) {
            // This shoots the exact error straight back to GitHub!
            return ResponseEntity.status(500).body("CRASH LOG: " + e.getMessage());
        }
    }
}