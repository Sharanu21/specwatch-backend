package com.specwatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminProjectDto {
    private Long id;
    private String name;
    private String ownerEmail;
    private String repoFullName;
    private String branch;
    private String createdAt;
    private String lastCheckedAt;
}
