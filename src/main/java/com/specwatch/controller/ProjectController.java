package com.specwatch.controller;

import com.specwatch.dto.ProjectRequest;
import com.specwatch.model.ChangeReport;
import com.specwatch.model.Project;
import com.specwatch.model.User;
import com.specwatch.repository.ChangeReportRepository;
import com.specwatch.repository.ProjectRepository;
import com.specwatch.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ChangeReportRepository changeReportRepository;

    /**
     * Serialize a Project to a safe API map.
     * Never exposes: githubToken, slackWebhookUrl (raw), discordWebhookUrl (raw), lastSpecContent.
     */
    private Map<String, Object> toMap(Project p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("repoFullName", p.getRepoFullName());
        map.put("specFilePath", p.getSpecFilePath());
        map.put("branch", p.getBranch());
        map.put("webhookToken", p.getWebhookToken());
        map.put("lastSpecVersion", p.getLastSpecVersion());
        map.put("lastCheckedAt", p.getLastCheckedAt());
        map.put("createdAt", p.getCreatedAt());
        // Return boolean flags only — never the actual secret values
        map.put("slackWebhookUrl", p.getSlackWebhookUrl() != null && !p.getSlackWebhookUrl().isBlank());
        map.put("discordWebhookUrl", p.getDiscordWebhookUrl() != null && !p.getDiscordWebhookUrl().isBlank());
        map.put("hasGithubToken", p.getGithubToken() != null && !p.getGithubToken().isBlank());
        return map;
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getProjects(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = resolveUser(userDetails);
        List<Map<String, Object>> result = projectRepository
                .findByUserId(user.getId())
                .stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        User user = resolveUser(userDetails);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Access denied");
        }

        return ResponseEntity.ok(toMap(project));
    }

    @PostMapping
    public ResponseEntity<?> createProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ProjectRequest request
    ) {
        User user = resolveUser(userDetails);

        int repoCount = projectRepository.countByUserId(user.getId());
        int maxRepos = switch (user.getPlan()) {
            case FREE -> 1;
            case TEAM -> 10;
            case PRO  -> Integer.MAX_VALUE;
        };

        if (repoCount >= maxRepos) {
            return ResponseEntity.badRequest().body(
                    "Plan limit reached. Upgrade to add more repositories."
            );
        }

        Project project = new Project();
        project.setName(request.getName().trim());
        project.setRepoFullName(request.getRepoFullName().trim());
        project.setSpecFilePath(request.getSpecFilePath().trim());
        project.setBranch(
            request.getBranch() != null && !request.getBranch().isBlank()
                ? request.getBranch().trim() : "main"
        );
        project.setSlackWebhookUrl(
            request.getSlackWebhookUrl() != null && !request.getSlackWebhookUrl().isBlank()
                ? request.getSlackWebhookUrl().trim() : null
        );
        project.setDiscordWebhookUrl(
            request.getDiscordWebhookUrl() != null && !request.getDiscordWebhookUrl().isBlank()
                ? request.getDiscordWebhookUrl().trim() : null
        );
        project.setGithubToken(
            request.getGithubToken() != null && !request.getGithubToken().isBlank()
                ? request.getGithubToken().trim() : null
        );
        project.setWebhookToken(java.util.UUID.randomUUID().toString().replace("-", ""));
        project.setUser(user);

        projectRepository.save(project);
        return ResponseEntity.ok(toMap(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request
    ) {
        User user = resolveUser(userDetails);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Access denied");
        }

        project.setName(request.getName().trim());
        project.setSpecFilePath(request.getSpecFilePath().trim());
        project.setBranch(
            request.getBranch() != null && !request.getBranch().isBlank()
                ? request.getBranch().trim() : "main"
        );
        project.setSlackWebhookUrl(
            request.getSlackWebhookUrl() != null && !request.getSlackWebhookUrl().isBlank()
                ? request.getSlackWebhookUrl().trim() : null
        );
        project.setDiscordWebhookUrl(
            request.getDiscordWebhookUrl() != null && !request.getDiscordWebhookUrl().isBlank()
                ? request.getDiscordWebhookUrl().trim() : null
        );
        if (request.getGithubToken() != null) {
            project.setGithubToken(
                !request.getGithubToken().isBlank() ? request.getGithubToken().trim() : null
            );
        }

        projectRepository.save(project);
        return ResponseEntity.ok(toMap(project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        User user = resolveUser(userDetails);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Access denied");
        }

        projectRepository.delete(project);
        return ResponseEntity.ok("Deleted");
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(size, 50);

        User user = resolveUser(userDetails);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found"));

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Access denied");
        }

        Page<ChangeReport> history = changeReportRepository
                .findByProjectIdOrderByCreatedAtDesc(id, PageRequest.of(page, safeSize));

        return ResponseEntity.ok(history);
    }
}
