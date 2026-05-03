package com.specwatch.controller;

import com.specwatch.config.JwtUtil;
import com.specwatch.dto.AuthRequest;
import com.specwatch.dto.AuthResponse;
import com.specwatch.model.User;
import com.specwatch.repository.UserRepository;
import com.specwatch.service.EmailVerificationService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final EmailVerificationService emailVerificationService;

    @org.springframework.beans.factory.annotation.Value("${admin.email:}")
    private String adminEmail;

    private boolean isAdmin(String email) {
        return !adminEmail.isBlank() && adminEmail.equalsIgnoreCase(email);
    }

    private static final int MAX_BUCKET_ENTRIES = 50_000;

    // Per-IP rate limiter: max 5 requests per minute on auth endpoints
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket getBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build()
        );
    }

    /** Evict all buckets hourly to prevent unbounded memory growth under IP rotation attacks. */
    @Scheduled(fixedDelay = 3_600_000)
    public void evictBuckets() {
        if (buckets.size() > MAX_BUCKET_ENTRIES) {
            buckets.clear();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest
    ) {
        Bucket bucket = getBucket(httpRequest.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please wait a minute.");
        }

        if (userRepository.existsByEmail(request.getEmail().toLowerCase().trim())) {
            return ResponseEntity.badRequest().body("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail().toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setEmailVerified(false);
        user.setVerificationToken(java.util.UUID.randomUUID().toString());
        userRepository.save(user);

        emailVerificationService.sendVerificationEmail(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(
                token, user.getEmail(), user.getName(), user.getPlan().name(), isAdmin(user.getEmail())
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest
    ) {
        Bucket bucket = getBucket(httpRequest.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many login attempts. Please wait a minute.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(
                request.getEmail().toLowerCase().trim()
        );
        String token = jwtUtil.generateToken(userDetails);
        User user = userRepository.findByEmail(
                request.getEmail().toLowerCase().trim()
        ).orElseThrow();

        return ResponseEntity.ok(new AuthResponse(
                token, user.getEmail(), user.getName(), user.getPlan().name(), isAdmin(user.getEmail())
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        return ResponseEntity.ok(new AuthResponse(null, user.getEmail(), user.getName(), user.getPlan().name(), isAdmin(user.getEmail())));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        Bucket bucket = getBucket(httpRequest.getRemoteAddr());
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many attempts. Please wait a minute.");
        }

        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        if (currentPassword == null || currentPassword.isBlank()) {
            return ResponseEntity.badRequest().body("Current password is required");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            return ResponseEntity.badRequest().body("New password must be 8–128 characters");
        }

        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok("Password updated successfully");
    }
}
