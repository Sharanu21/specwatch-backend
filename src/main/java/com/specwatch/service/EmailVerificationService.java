
package com.specwatch.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.specwatch.model.User;
import com.specwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;

    @Value("${resend.api.key:}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.base-url:https://specwatch.netlify.app}")
    private String appBaseUrl;

    public void sendVerificationEmail(User user) {
        if (resendApiKey == null || resendApiKey.isBlank()) return;

        try {
            String verifyLink = appBaseUrl + "/verify-email?token=" + user.getVerificationToken();

            String html = """
                <!DOCTYPE html>
                <html>
                <body style="font-family: sans-serif; background: #0a0a0f; color: #e8e8f0; padding: 40px;">
                    <div style="max-width: 500px; margin: 0 auto;">
                        <div style="font-family: monospace; font-size: 20px; font-weight: 700; margin-bottom: 24px;">
                            Spec<span style="color: #ff4d6d;">Watch</span>
                        </div>
                        <h2>Verify your email</h2>
                        <p style="color: #6b6b80;">Thanks for signing up! Click below to verify your email address.</p>
                        <a href="%s" style="display:inline-block;background:#ff4d6d;color:white;padding:12px 24px;border-radius:6px;text-decoration:none;font-weight:600;margin:24px 0;">
                            Verify Email →
                        </a>
                        <p style="color: #6b6b80; font-size: 12px;">If you didn't create an account, ignore this email.</p>
                    </div>
                </body>
                </html>
                """.formatted(verifyLink);

            Resend resend = new Resend(resendApiKey);
            resend.emails().send(CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(user.getEmail())
                    .subject("Verify your SpecWatch email")
                    .html(html)
                    .build());

            log.info("Verification email sent to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send verification email: {}", e.getMessage());
        }
    }

    public boolean verifyEmail(String token) {
        Optional<User> userOpt = userRepository.findByVerificationToken(token);
        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        return true;
    }
}