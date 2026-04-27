package com.specwatch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // e.g. "octocat/hello-world"
    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    // path to OpenAPI file inside the repo e.g. "docs/openapi.yaml"
    @Column(name = "spec_file_path", nullable = false)
    private String specFilePath;

    // branch to monitor e.g. "main"
    @Column(name = "branch", nullable = false)
    private String branch = "main";

    // Slack webhook URL for this project
    @Column(name = "slack_webhook_url")
    private String slackWebhookUrl;

    // Discord webhook URL for this project
    @Column(name = "discord_webhook_url")
    private String discordWebhookUrl;

    // Last known spec content stored as text — we diff against this
    @Column(name = "last_spec_content", columnDefinition = "TEXT")
    private String lastSpecContent;

    @Column(name = "last_spec_version")
    private String lastSpecVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @JsonIgnore // 🚨 Prevents the infinite loop back to the User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JsonIgnore // 🚨 Prevents infinite loop into the Change Reports array
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ChangeReport> changeReports;
}