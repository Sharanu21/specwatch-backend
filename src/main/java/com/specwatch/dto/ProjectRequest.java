package com.specwatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 100, message = "Project name too long")
    private String name;

    @NotBlank(message = "GitHub repo is required")
    @Pattern(
        regexp = "^[\\w.\\-]+/[\\w.\\-]+$",
        message = "Repo must be in owner/repo format (e.g. octocat/hello-world)"
    )
    @Size(max = 200, message = "Repo name too long")
    private String repoFullName;

    @NotBlank(message = "Spec file path is required")
    @Size(max = 500, message = "File path too long")
    private String specFilePath;

    @Size(max = 100, message = "Branch name too long")
    private String branch = "main";

    @Size(max = 500, message = "Slack webhook URL too long")
    private String slackWebhookUrl;

    @Size(max = 500, message = "Discord webhook URL too long")
    private String discordWebhookUrl;

    @Size(max = 255, message = "GitHub token too long")
    private String githubToken;
}
