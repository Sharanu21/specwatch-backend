
package com.specwatch.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.specwatch.model.User;
import com.specwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.base-url:https://specwatch.netlify.app}")
    private String appBaseUrl;

    public boolean sendResetEmail(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            // Don't reveal if email exists
            return true;
        }

        User user = userOpt.get();
        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        String resetLink = appBaseUrl + "/reset-password?token=" + token;

        try {
            Resend resend = new Resend(resendApiKey);
            String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; background: #0a0a0f; color: #e8e8f0; padding: 40px;">
                    <div style="max-width: 500px; margin: 0 auto;">
                        <div style="font-family: monospace; font-size: 20px; font-weight: 700; margin-bottom: 24px;">
                            Spec<span style="color: #ff4d6d;">Watch</span>
                        </div>
                        <h2 style="color: #e8e8f0;">Reset your password</h2>
                        <p style="color: #6b6b80;">Click the button below to reset your password. This link expires in 1 hour.</p>
                        <a href="%s" style="display: inline-block; background: #ff4d6d; color: white; padding: 12px 24px; border-radius: 6px; text-decoration: none; font-weight: 600; margin: 24px 0;">
                            Reset Password →
                        </a>
                        <p style="color: #6b6b80; font-size: 12px;">If you didn't request this, ignore this email.</p>
                    </div>
                </body>
                </html>
                """.formatted(resetLink);

            resend.emails().send(CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(email)
                    .subject("Reset your SpecWatch password")
                    .html(html)
                    .build());

            log.info("Password reset email sent to {}", email);
            return true;
        } catch (Exception e) {
            log.error("Failed to send reset email: {}", e.getMessage());
            return false;
        }
    }

    public boolean resetPassword(String token, String newPassword) {
        Optional<User> userOpt = userRepository.findByResetToken(token);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            return false; // Token expired
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        return true;
    }
}