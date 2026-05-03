package com.specwatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String email;
    private String name;
    private String plan;
    private String provider;
    private boolean emailVerified;
    private String createdAt;
    private int projectCount;
}
