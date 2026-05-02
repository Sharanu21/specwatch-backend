package com.specwatch.controller;

import com.specwatch.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body("Email is required");
        }
        passwordResetService.sendResetEmail(email);
        return ResponseEntity.ok("If that email exists, a reset link has been sent.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");

        if (token == null || newPassword == null || newPassword.length() < 8) {
            return ResponseEntity.badRequest().body("Invalid request");
        }

        boolean success = passwordResetService.resetPassword(token, newPassword);
        if (!success) {
            return ResponseEntity.badRequest().body("Invalid or expired reset link");
        }
        return ResponseEntity.ok("Password reset successfully");
    }
}