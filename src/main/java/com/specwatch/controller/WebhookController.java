package com.specwatch.controller;

import com.specwatch.repository.ProjectRepository;
import com.specwatch.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final ProjectRepository projectRepository;

    @Value("${github.webhook.secret:}")
    private String globalWebhookSecret;

    /**
     * Verify the X-Hub-Signature-256 header GitHub sends with every webhook delivery.
     * Falls back gracefully if no global secret is configured (uses per-project token only).
     */
    private boolean verifyGitHubSignature(String payload, String signatureHeader) {
        if (globalWebhookSecret == null || globalWebhookSecret.isBlank()) {
            // No global HMAC secret configured — skip HMAC check, rely on per-project token
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("Missing or malformed X-Hub-Signature-256 header");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                globalWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = "sha256=" + HexFormat.of().formatHex(hash);
            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("HMAC verification failed: {}", e.getMessage());
            return false;
        }
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestParam(value = "token", defaultValue = "") String token,
            @RequestBody String payload
    ) {
        // Verify GitHub HMAC signature
        if (!verifyGitHubSignature(payload, signature)) {
            log.warn("Webhook rejected: invalid HMAC signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            webhookService.handlePushEvent(payload, token);
            return ResponseEntity.ok("Processed");
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Processing failed");
        }
    }
}
