package com.deallock.backend.controllers;


import com.deallock.backend.dtos.RegisterDto;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.UserRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password4j.BcryptPassword4jPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Date;

@AllArgsConstructor
@Controller
@RequestMapping("/register")
public class RegisterController {


    private final UserRepository userRepository;
    @GetMapping
    public String register(Model model){
        model.addAttribute(new RegisterDto());

        return "register";
    }
    @PostMapping
    public String register (@Valid @ModelAttribute RegisterDto registerDto){
        User user = User.builder()
                .fullName(registerDto.getFullName())
                .email(registerDto.getEmail())
                .password(new BCryptPasswordEncoder().encode(registerDto.getPassword()))
                .address(registerDto.getAddress())
                .role("Role.USER.name()")
                .creation(new Date())
                .build();
        userRepository.save(user);
        return "register";
    }


}
