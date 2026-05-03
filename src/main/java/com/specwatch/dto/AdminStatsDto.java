package com.specwatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long totalProjects;
    private long newUsersThisWeek;
    private long newProjectsThisWeek;
}
