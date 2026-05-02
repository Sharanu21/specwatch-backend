package com.specwatch.controller;

import com.specwatch.repository.ProjectRepository;
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
    private final ProjectRepository projectRepository;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        long projectCount = projectRepository.count();
        return ResponseEntity.ok("Backend alive. Projects in DB: " + projectCount);
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "") String event,
            @RequestParam(value = "token", defaultValue = "") String token,
            @RequestBody String payload
    ) {
        if (!"push".equals(event)) {
            return ResponseEntity.ok("Event ignored: " + event);
        }

        try {
            webhookService.handlePushEvent(payload, token);
            return ResponseEntity.ok("Webhook received and processed");
        } catch (Exception e) {
            return ResponseEntity.ok("ERROR: " + e.getMessage());
        }
    }
}