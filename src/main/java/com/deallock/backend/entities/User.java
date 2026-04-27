package com.deallock.backend.entities;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;


@Getter
@Setter
@NoArgsConstructor
@Entity
@Table (name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;


    private String fullName;
    private String email;
    private String username;
    private String password;
    @Transient
    private String confirmPassword;
    private String address;
    private String phone;
    private LocalDate dateOfBirth;
    private String role;
    private boolean enabled;
    private int failedLoginAttempts;
    private Instant lockoutUntil;
    private String profileImageUrl;
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] profileImage;
    private String profileImageContentType;
    private String profileImageKey;

    private Instant creation;

    @Builder
    public User(String fullName,
                String email,
                String username,
                String password,
                String confirmPassword,
                String address,
                String phone,
                LocalDate dateOfBirth,
                String role,
                boolean enabled,
                int failedLoginAttempts,
                Instant lockoutUntil,
                String profileImageUrl,
                byte[] profileImage,
                String profileImageContentType,
                String profileImageKey,
                Instant creation) {
        this.fullName = fullName;
        this.email = email;
        this.username = username;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.address = address;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.role = role;
        this.enabled = enabled;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockoutUntil = lockoutUntil;
        this.profileImageUrl = profileImageUrl;
        this.profileImage = profileImage;
        this.profileImageContentType = profileImageContentType;
        this.profileImageKey = profileImageKey;
        this.creation = creation;
    }


}






