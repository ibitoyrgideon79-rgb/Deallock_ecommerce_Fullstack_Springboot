package com.deallock.backend.controllers;

import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.CurrentUserService;
import com.deallock.backend.services.FileStorageService;
import java.io.IOException;
import java.security.Principal;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ProfileController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/*");

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

    public ProfileController(UserRepository userRepository,
                             CurrentUserService currentUserService,
                             FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/profile")
    public String profile() {
        return "redirect:/dashboard";
    }

    @PostMapping("/profile/upload")
    public String uploadProfileImage(@RequestParam("profileImage") MultipartFile file,
                                     Principal principal) throws IOException {
        if (principal == null || file == null || file.isEmpty()) {
            return "redirect:/dashboard?upload=failed";
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            return "redirect:/dashboard?upload=too-large";
        }

        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return "redirect:/dashboard?upload=failed";
        }

        var user = userOpt.get();
        try {
            FileStorageService.StoredFile stored = fileStorageService.save("users/profile-images", file, MAX_UPLOAD_BYTES, IMAGE_TYPES);
            user.setProfileImage(null);
            user.setProfileImageContentType(stored.contentType());
            user.setProfileImageKey(stored.key());
        } catch (IOException ex) {
            // Fallback to DB blob if filesystem storage isn't available.
            user.setProfileImage(file.getBytes());
            user.setProfileImageContentType(file.getContentType());
            user.setProfileImageKey(null);
        }
        userRepository.save(user);

        return "redirect:/dashboard?upload=success";
    }

    @GetMapping("/profile/image")
    public ResponseEntity<byte[]> profileImage(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var user = userOpt.get();
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (user.getProfileImageContentType() != null) {
            type = MediaType.parseMediaType(user.getProfileImageContentType());
        }
        byte[] bytes = user.getProfileImage();
        if ((bytes == null || bytes.length == 0) && user.getProfileImageKey() != null && !user.getProfileImageKey().isBlank()) {
            try {
                bytes = fileStorageService.read(user.getProfileImageKey());
            } catch (IOException ignored) {
                bytes = null;
            }
        }
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(type)
                .body(bytes);
    }
}
