
package com.specwatch.controller;

import com.specwatch.dto.ProjectRequest;
import com.specwatch.model.ChangeReport;
import com.specwatch.model.Project;
import com.specwatch.model.User;
import com.specwatch.repository.ChangeReportRepository;
import com.specwatch.repository.ProjectRepository;
import com.specwatch.repository.UserRepository;
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

    private Map<String, Object> toMap(Project p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("repoFullName", p.getRepoFullName());
        map.put("specFilePath", p.getSpecFilePath());
        map.put("branch", p.getBranch());

        // 🚨 FIX: Return boolean true/false instead of exposing the secret URL
        map.put("slackWebhookUrl", p.getSlackWebhookUrl() != null && !p.getSlackWebhookUrl().isBlank());
        map.put("discordWebhookUrl", p.getDiscordWebhookUrl() != null && !p.getDiscordWebhookUrl().isBlank());

        map.put("lastSpecVersion", p.getLastSpecVersion());
        map.put("lastSpecContent", p.getLastSpecContent());
        map.put("lastCheckedAt", p.getLastCheckedAt());
        map.put("createdAt", p.getCreatedAt());
        return map;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getProjects(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        List<Map<String, Object>> result = projectRepository
                .findByUserId(user.getId())
                .stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ProjectRequest request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();

        int repoCount = projectRepository.countByUserId(user.getId());
        int maxRepos = switch (user.getPlan()) {
            case FREE -> 1;
            case TEAM -> 10;
            case PRO -> Integer.MAX_VALUE;
        };

        if (repoCount >= maxRepos) {
            return ResponseEntity.badRequest().body(
                    "Plan limit reached. Upgrade to add more repositories."
            );
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setRepoFullName(request.getRepoFullName());
        project.setSpecFilePath(request.getSpecFilePath());
        project.setBranch(request.getBranch() != null ? request.getBranch() : "main");

        // 🚨 FIX: Prevent saving empty strings as URLs
        project.setSlackWebhookUrl(
                request.getSlackWebhookUrl() != null && !request.getSlackWebhookUrl().isBlank()
                        ? request.getSlackWebhookUrl() : null
        );
        project.setDiscordWebhookUrl(
                request.getDiscordWebhookUrl() != null && !request.getDiscordWebhookUrl().isBlank()
                        ? request.getDiscordWebhookUrl() : null
        );

        project.setUser(user);

        projectRepository.save(project);
        return ResponseEntity.ok(toMap(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestBody ProjectRequest request
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Project project = projectRepository.findById(id).orElseThrow();

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Not your project");
        }

        project.setName(request.getName());
        project.setSpecFilePath(request.getSpecFilePath());
        project.setBranch(request.getBranch());

        // 🚨 FIX: Prevent saving empty strings as URLs
        project.setSlackWebhookUrl(
                request.getSlackWebhookUrl() != null && !request.getSlackWebhookUrl().isBlank()
                        ? request.getSlackWebhookUrl() : null
        );
        project.setDiscordWebhookUrl(
                request.getDiscordWebhookUrl() != null && !request.getDiscordWebhookUrl().isBlank()
                        ? request.getDiscordWebhookUrl() : null
        );

        projectRepository.save(project);
        return ResponseEntity.ok(toMap(project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProject(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Project project = projectRepository.findById(id).orElseThrow();

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Not your project");
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
        User user = userRepository.findByEmail(userDetails.getUsername()).orElseThrow();
        Project project = projectRepository.findById(id).orElseThrow();

        if (!project.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body("Not your project");
        }

        Page<ChangeReport> history = changeReportRepository
                .findByProjectIdOrderByCreatedAtDesc(id, PageRequest.of(page, size));

        return ResponseEntity.ok(history);
    }
}