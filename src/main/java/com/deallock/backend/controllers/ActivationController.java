package com.deallock.backend.controllers;

import com.deallock.backend.repositories.ActivationTokenRepository;
import com.deallock.backend.repositories.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ActivationController {

    private final ActivationTokenRepository activationRepo;
    private final UserRepository userRepository;

    public ActivationController(ActivationTokenRepository activationRepo, UserRepository userRepository) {
        this.activationRepo = activationRepo;
        this.userRepository = userRepository;
    }

    @GetMapping("/activate")
    public String activate(@RequestParam("token") String token) {
        var tokenOpt = activationRepo.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return "redirect:/login?activated=false";
        }

        var entry = tokenOpt.get();
        if (entry.isUsed() || entry.getExpiresAt().isBefore(Instant.now())) {
            return "redirect:/login?activated=false";
        }

        var userOpt = userRepository.findByEmail(entry.getEmail());
        if (userOpt.isEmpty()) {
            return "redirect:/login?activated=false";
        }

        var user = userOpt.get();
        user.setEnabled(true);
        userRepository.save(user);

        entry.setUsed(true);
        activationRepo.save(entry);

        return "redirect:/login?activated=true";
    }
}
