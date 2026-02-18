package com.deallock.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterDto {

    @NotEmpty
    private String fullName;

    @NotEmpty
    @Email
    private String email;

    @Size(min = 6, message = "Password must be at least 6 characters long")
    private String password;
    private String confirmPassword;
    private String address;
    private String username;
    private String otp;
    private LocalDate dateOfBirth;
}