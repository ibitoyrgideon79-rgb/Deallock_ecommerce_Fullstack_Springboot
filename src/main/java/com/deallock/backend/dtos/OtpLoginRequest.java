package com.deallock.backend.dtos;

import jakarta.validation.constraints.NotBlank;

public class OtpLoginRequest {
    @NotBlank
    public String login;
    @NotBlank
    public String otp;
    public String channel;
}

