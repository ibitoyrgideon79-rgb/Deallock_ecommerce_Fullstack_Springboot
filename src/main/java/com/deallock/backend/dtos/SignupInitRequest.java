package com.deallock.backend.dtos;

import jakarta.validation.constraints.Email;

public class SignupInitRequest {
    @Email
    public String email;
    public String phone;
    public String channel;
}

