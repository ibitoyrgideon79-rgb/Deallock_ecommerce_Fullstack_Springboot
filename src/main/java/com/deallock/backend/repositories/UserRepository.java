package com.deallock.backend.repositories;

import java.util.Optional;
import java.util.List;
import com.deallock.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);
    List<User> findByRole(String role);

}
