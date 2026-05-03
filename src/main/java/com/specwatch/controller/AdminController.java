package com.specwatch.controller;

import com.specwatch.dto.AdminProjectDto;
import com.specwatch.dto.AdminStatsDto;
import com.specwatch.dto.AdminUserDto;
import com.specwatch.repository.ProjectRepository;
import com.specwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Value("${admin.email:}")
    private String adminEmail;

    private boolean isAdmin(UserDetails userDetails) {
        return !adminEmail.isBlank() &&
               adminEmail.equalsIgnoreCase(userDetails.getUsername());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        long totalUsers    = userRepository.count();
        long totalProjects = projectRepository.count();
        long newUsers      = userRepository.countByCreatedAtAfter(weekAgo);
        long newProjects   = projectRepository.countByCreatedAtAfter(weekAgo);

        return ResponseEntity.ok(new AdminStatsDto(totalUsers, totalProjects, newUsers, newProjects));
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<AdminUserDto> result = userRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(u -> new AdminUserDto(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getPlan().name(),
                u.getProvider() != null ? u.getProvider() : "LOCAL",
                Boolean.TRUE.equals(u.getEmailVerified()),
                u.getCreatedAt() != null ? u.getCreatedAt().format(fmt) : "-",
                projectRepository.countByUserId(u.getId())
            ))
            .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/projects")
    public ResponseEntity<?> projects(@AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<AdminProjectDto> result = projectRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .map(p -> new AdminProjectDto(
                p.getId(),
                p.getName(),
                p.getUser() != null ? p.getUser().getEmail() : "-",
                p.getRepoFullName(),
                p.getBranch(),
                p.getCreatedAt() != null ? p.getCreatedAt().format(fmt) : "-",
                p.getLastCheckedAt() != null ? p.getLastCheckedAt().format(fmt) : "Never"
            ))
            .toList();

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!isAdmin(userDetails)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
        if (!userRepository.existsById(id)) return ResponseEntity.notFound().build();

        userRepository.deleteById(id);
        log.warn("Admin deleted user id={}", id);
        return ResponseEntity.ok("User deleted");
    }
}
