package com.specwatch.dto;

import lombok.Data;

@Data
public class ProjectResponse {
    private Long id;
    private String name;
    private String repoFullName;
    private String specFilePath;
    private String branch;
    private boolean hasSlack;
    private boolean hasDiscord;
    private String lastSpecVersion;
    private String lastCheckedAt;
    private int totalReports;
    private int breakingReports;
}
