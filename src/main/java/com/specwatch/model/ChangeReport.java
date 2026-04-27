package com.specwatch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "change_reports")
@Data
@NoArgsConstructor
public class ChangeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚨 FIX: Prevents Jackson from crashing when turning this into JSON
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // Git commit SHA that triggered this
    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "pushed_by")
    private String pushedBy;

    // Did ANY breaking change occur?
    @Column(name = "has_breaking_changes", nullable = false)
    private boolean hasBreakingChanges = false;

    @Column(name = "breaking_count")
    private int breakingCount = 0;

    @Column(name = "non_breaking_count")
    private int nonBreakingCount = 0;

    // Full human-readable summary generated from the diff
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    // JSON array of individual change items (stored as text)
    @Column(name = "changes_json", columnDefinition = "TEXT")
    private String changesJson;

    @Column(name = "old_version")
    private String oldVersion;

    @Column(name = "new_version")
    private String newVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Was Slack/Discord notification sent successfully?
    @Column(name = "notification_sent")
    private boolean notificationSent = false;
}