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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

    // One bucket per IP — max 5 login attempts per minute
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket getBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build()
        );
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody AuthRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
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

        // Send verification email
        emailVerificationService.sendVerificationEmail(user);

        // Return token so they can use app (verification optional for now)
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(
                token, user.getEmail(), user.getName(), user.getPlan().name()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody AuthRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest
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
                token, user.getEmail(), user.getName(), user.getPlan().name()
        ));
    }
}