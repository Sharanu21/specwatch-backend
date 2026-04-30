package com.specwatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.specwatch.dto.DiffResult;
import com.specwatch.model.ChangeReport;
import com.specwatch.model.Project;
import com.specwatch.repository.ChangeReportRepository;
import com.specwatch.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookService {

    private final EmailService emailService;
    private final ProjectRepository projectRepository;
    private final ChangeReportRepository changeReportRepository;
    private final GitHubService gitHubService;
    private final SpecDiffService specDiffService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void handlePushEvent(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);

            String repoFullName = payload.path("repository").path("full_name").asText();
            String ref = payload.path("ref").asText();
            String branch = ref.replace("refs/heads/", "");
            String commitSha = payload.path("after").asText();
            String pushedBy = payload.path("pusher").path("name").asText();
            String commitMessage = payload.path("head_commit").path("message").asText();

            log.info("Processing push: repo={} branch={} commit={}", repoFullName, branch, commitSha);

            Optional<Project> projectOpt = projectRepository
                    .findFirstByRepoFullNameAndBranch(repoFullName, branch);

            if (projectOpt.isEmpty()) {
                log.info("No project found for {}/{} — skipping", repoFullName, branch);
                return;
            }

            Project project = projectOpt.get();

            String newSpec = gitHubService.fetchFileContent(
                    repoFullName,
                    project.getSpecFilePath(),
                    commitSha
            );

            if (newSpec == null || newSpec.isBlank()) {
                log.error("Empty spec file returned from GitHub for {}", project.getSpecFilePath());
                return;
            }

            String oldSpec = project.getLastSpecContent();
            DiffResult result = specDiffService.diff(oldSpec, newSpec);

            ChangeReport report = new ChangeReport();
            report.setProject(project);
            report.setCommitSha(commitSha);
            report.setCommitMessage(commitMessage);
            report.setPushedBy(pushedBy);
            report.setHasBreakingChanges(result.isHasBreakingChanges());
            report.setBreakingCount(result.getBreakingCount());
            report.setNonBreakingCount(result.getNonBreakingCount());
            report.setSummary(result.getSummary());
            report.setChangesJson(result.getChangesJson());
            report.setOldVersion(project.getLastSpecVersion());

            String newVersion = extractVersion(newSpec);
            report.setNewVersion(newVersion);

            // --- REPLACED NOTIFICATION BLOCK START ---
            if (!result.isFirstRun() && !result.isError()) {
                try {
                    boolean slackSent = notificationService.sendSlackAlert(project, result, commitSha, pushedBy);
                    boolean discordSent = notificationService.sendDiscordAlert(project, result, commitSha, pushedBy);

                    // Email removed temporarily to prevent LazyInitializationException rolling back the transaction
                    report.setNotificationSent(slackSent || discordSent);
                } catch (Exception notifEx) {
                    log.error("Notification failed but continuing: {}", notifEx.getMessage());
                    report.setNotificationSent(false);
                }
            }
            // --- REPLACED NOTIFICATION BLOCK END ---

            changeReportRepository.save(report);

            project.setLastSpecContent(newSpec);
            project.setLastSpecVersion(newVersion);
            project.setLastCheckedAt(LocalDateTime.now());
            projectRepository.save(project);

            log.info("✅ Processed push for {}: breaking={}, nonBreaking={}",
                    project.getName(), result.getBreakingCount(), result.getNonBreakingCount());

        } catch (Exception e) {
            log.error("Error processing push webhook: {}", e.getMessage(), e);
        }
    }

    private String extractVersion(String spec) {
        try {
            JsonNode node;
            if (spec.trim().startsWith("{")) {
                node = objectMapper.readTree(spec);
            } else {
                com.fasterxml.jackson.dataformat.yaml.YAMLMapper yamlMapper =
                        new com.fasterxml.jackson.dataformat.yaml.YAMLMapper();
                node = yamlMapper.readTree(spec);
            }
            return node.path("info").path("version").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}