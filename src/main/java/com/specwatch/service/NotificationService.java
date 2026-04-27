package com.specwatch.service;

import com.specwatch.dto.ChangeItem;
import com.specwatch.dto.DiffResult;
import com.specwatch.model.Project;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private final WebClient webClient = WebClient.create();

    public boolean sendSlackAlert(Project project, DiffResult result, String commitSha, String pushedBy) {
        if (project.getSlackWebhookUrl() == null || project.getSlackWebhookUrl().isBlank()) {
            return false;
        }

        try {
            String color = result.isHasBreakingChanges() ? "#ff4d6d" : "#00d9ff";
            String emoji = result.isHasBreakingChanges() ? "🚨" : "✅";
            String title = result.isHasBreakingChanges()
                    ? "🚨 " + result.getBreakingCount() + " Breaking Change(s) Detected — Action Required!"
                    : "✅ API Updated — No Breaking Changes";

            String text = buildSlackText(result, commitSha, pushedBy, project.getRepoFullName());

            Map<String, Object> payload = new HashMap<>();
            payload.put("text", emoji + " *SpecWatch* — " + project.getName());
            payload.put("attachments", new Object[]{
                    Map.of(
                            "color", color,
                            "title", title,
                            "text", text,
                            "footer", "SpecWatch • " + project.getRepoFullName(),
                            "ts", System.currentTimeMillis() / 1000
                    )
            });

            webClient.post()
                    .uri(project.getSlackWebhookUrl())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Slack alert sent for project {}", project.getName());
            return true;

        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendDiscordAlert(Project project, DiffResult result, String commitSha, String pushedBy) {
        if (project.getDiscordWebhookUrl() == null || project.getDiscordWebhookUrl().isBlank()) {
            return false;
        }

        try {
            int color = result.isHasBreakingChanges() ? 0xff4d6d : 0x00d9ff;
            String title = result.isHasBreakingChanges()
                    ? "🚨 Breaking API Change Detected"
                    : "✅ API Updated — No Breaking Changes";

            String description = buildDiscordText(result, commitSha, pushedBy);

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", title);
            embed.put("description", description);
            embed.put("color", color);
            embed.put("footer", Map.of("text", "SpecWatch • " + project.getRepoFullName()));

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", "SpecWatch");
            payload.put("embeds", new Object[]{embed});

            webClient.post()
                    .uri(project.getDiscordWebhookUrl())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Discord alert sent for project {}", project.getName());
            return true;

        } catch (Exception e) {
            log.error("Failed to send Discord alert: {}", e.getMessage());
            return false;
        }
    }

    private String buildSlackText(DiffResult result, String commitSha, String pushedBy, String repo) {
        StringBuilder sb = new StringBuilder();

        if (commitSha != null) {
            sb.append("*Commit:* `").append(commitSha, 0, Math.min(7, commitSha.length())).append("`");
            if (pushedBy != null) sb.append("  |  *By:* ").append(pushedBy);
            sb.append("\n");
        }

        sb.append("*Repo:* `").append(repo).append("`\n\n");

        if (result.getBreakingCount() > 0) {
            sb.append("*").append(result.getBreakingCount())
                    .append(" Breaking Change")
                    .append(result.getBreakingCount() > 1 ? "s" : "")
                    .append("*\n");

            if (result.getChanges() != null) {
                result.getChanges().stream()
                        .filter(c -> c.getType() == ChangeItem.Type.BREAKING)
                        .forEach(c -> sb
                                .append("  ✗  `").append(c.getEndpoint()).append("`\n")
                                .append("      _").append(c.getDescription()).append("_\n")
                        );
            }
        }

        if (result.getNonBreakingCount() > 0) {
            if (result.getBreakingCount() > 0) sb.append("\n");
            sb.append("*").append(result.getNonBreakingCount())
                    .append(" Non-Breaking Change")
                    .append(result.getNonBreakingCount() > 1 ? "s" : "")
                    .append("*\n");

            if (result.getChanges() != null) {
                result.getChanges().stream()
                        .filter(c -> c.getType() == ChangeItem.Type.NON_BREAKING)
                        .forEach(c -> sb
                                .append("  ⚠  `").append(c.getEndpoint()).append("`\n")
                                .append("      _").append(c.getDescription()).append("_\n")
                        );
            }
        }

        return sb.toString();
    }

    private String buildDiscordText(DiffResult result, String commitSha, String pushedBy) {
        StringBuilder sb = new StringBuilder();

        if (commitSha != null) {
            sb.append("**Commit:** `")
                    .append(commitSha, 0, Math.min(7, commitSha.length()))
                    .append("`");
            if (pushedBy != null) sb.append(" by **").append(pushedBy).append("**");
            sb.append("\n\n");
        }

        if (result.getBreakingCount() > 0) {
            sb.append("**").append(result.getBreakingCount())
                    .append(" Breaking Change")
                    .append(result.getBreakingCount() > 1 ? "s" : "")
                    .append("**\n");

            if (result.getChanges() != null) {
                result.getChanges().stream()
                        .filter(c -> c.getType() == ChangeItem.Type.BREAKING)
                        .forEach(c -> sb
                                .append("✗ `").append(c.getEndpoint()).append("`\n")
                                .append("  ").append(c.getDescription()).append("\n")
                        );
            }
        }

        if (result.getNonBreakingCount() > 0) {
            if (result.getBreakingCount() > 0) sb.append("\n");
            sb.append("**").append(result.getNonBreakingCount())
                    .append(" Non-Breaking Change")
                    .append(result.getNonBreakingCount() > 1 ? "s" : "")
                    .append("**\n");

            if (result.getChanges() != null) {
                result.getChanges().stream()
                        .filter(c -> c.getType() == ChangeItem.Type.NON_BREAKING)
                        .forEach(c -> sb
                                .append("⚠ `").append(c.getEndpoint()).append("`\n")
                                .append("  ").append(c.getDescription()).append("\n")
                        );
            }
        }

        return sb.toString();
    }
}