package com.deallock.backend.repositories;

import java.util.Optional;
import com.deallock.backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);

}
