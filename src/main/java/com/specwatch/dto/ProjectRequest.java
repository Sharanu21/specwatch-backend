package com.specwatch.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String repoFullName; // e.g. "octocat/hello-world"

    @NotBlank
    private String specFilePath; // e.g. "docs/openapi.yaml"

    private String branch = "main";

    private String slackWebhookUrl;

    private String discordWebhookUrl;
}
