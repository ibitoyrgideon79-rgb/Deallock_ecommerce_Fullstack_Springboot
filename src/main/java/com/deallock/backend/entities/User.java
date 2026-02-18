package com.deallock.backend.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Date;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table (name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;


    private String fullName;
    private String email;
    private String username;
    private String password;
    private String confirmPassword;
    private String address;
    private LocalDate dateOfBirth;
    private String role;
    private boolean enabled;

    @Temporal(TemporalType.TIMESTAMP)
    private Date creation;


}






