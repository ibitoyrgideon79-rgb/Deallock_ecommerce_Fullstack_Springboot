package com.deallock.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public class CompleteProfileRequest {
    @NotBlank
    @Email
    public String email;

    public String phone;

    @NotBlank
    public String fullName;

    public String username;
    public String address;
    public LocalDate dateOfBirth;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[\\W_]).{8,}$",
            message = "Password must be 8+ chars with upper, lower, number, special"
    )
    public String password;

    @NotBlank
    public String confirmPassword;
}

