package com.specwatch.controller;

import com.specwatch.service.EmailVerificationService;
import com.specwatch.service.PasswordResetService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    // Stricter rate limit for password reset: 3 per 10 minutes per IP
    private final Map<String, Bucket> resetBuckets = new ConcurrentHashMap<>();

    private Bucket getResetBucket(String ip) {
        return resetBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(10))))
                .build()
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        Bucket bucket = getResetBucket(httpRequest.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please wait before requesting another reset link.");
        }

        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        // Always return the same message — never reveal whether email exists
        passwordResetService.sendResetEmail(email.toLowerCase().trim());
        return ResponseEntity.ok("If that email exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        Bucket bucket = getResetBucket(httpRequest.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please wait.");
        }

        String token = body.get("token");
        String newPassword = body.get("password");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Reset token is required");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            return ResponseEntity.badRequest().body("Password must be 8–128 characters");
        }

        boolean success = passwordResetService.resetPassword(token, newPassword);
        if (!success) {
            return ResponseEntity.badRequest().body("Invalid or expired reset link");
        }
        return ResponseEntity.ok("Password reset successfully");
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Token is required");
        }
        boolean success = emailVerificationService.verifyEmail(token);
        if (!success) {
            return ResponseEntity.badRequest().body("Invalid or expired verification link");
        }
        return ResponseEntity.ok("Email verified successfully! You can now login.");
    }
}
