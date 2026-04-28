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

    private final ProjectRepository projectRepository;
    private final ChangeReportRepository changeReportRepository;
    private final GitHubService gitHubService;
    private final SpecDiffService specDiffService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void handlePushEvent(String payloadJson) throws Exception {
        JsonNode payload = objectMapper.readTree(payloadJson);

        // Extract repo info
        String repoFullName = payload.path("repository").path("full_name").asText();
        String ref = payload.path("ref").asText();
        String branch = ref.replace("refs/heads/", "");
        String commitSha = payload.path("after").asText();
        String pushedBy = payload.path("pusher").path("name").asText();
        String commitMessage = payload.path("head_commit").path("message").asText();

        Optional<Project> projectOpt = projectRepository.findFirstByRepoFullNameAndBranch(repoFullName, branch);

        if (projectOpt.isEmpty()) {
            throw new Exception("No project found in DB for Repo: [" + repoFullName + "] and Branch: [" + branch + "]");
        }

        Project project = projectOpt.get();

        // Fetch the new spec from GitHub
        String newSpec = gitHubService.fetchFileContent(
                repoFullName,
                project.getSpecFilePath(),
                commitSha
        );

        if (newSpec == null || newSpec.isBlank()) {
            throw new Exception("GitHub returned an empty file for path: " + project.getSpecFilePath());
        }

        // Diff against stored spec
        String oldSpec = project.getLastSpecContent();
        DiffResult result = specDiffService.diff(oldSpec, newSpec);

        // Save report
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

        if (!result.isFirstRun() && !result.isError()) {
            boolean slackSent = notificationService.sendSlackAlert(project, result, commitSha, pushedBy);
            boolean discordSent = notificationService.sendDiscordAlert(project, result, commitSha, pushedBy);
            report.setNotificationSent(slackSent || discordSent);
        }

        changeReportRepository.save(report);

        project.setLastSpecContent(newSpec);
        project.setLastSpecVersion(newVersion);
        project.setLastCheckedAt(LocalDateTime.now());
        projectRepository.save(project);
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