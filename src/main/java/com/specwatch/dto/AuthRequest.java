package com.specwatch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {

    @Email(message = "Must be a valid email address")
    @NotBlank(message = "Email is required")
    @Size(max = 254, message = "Email too long")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8–128 characters")
    private String password;

    @Size(max = 100, message = "Name too long")
    private String name;
}
