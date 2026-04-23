package com.deallock.backend.repositories;

import com.deallock.backend.entities.NewsletterSubscription;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, Long> {

    Optional<NewsletterSubscription> findByEmail(String email);

    List<NewsletterSubscription> findByActiveTrueAndLastDigestSentAtBefore(Instant cutoff);

    List<NewsletterSubscription> findByActiveTrueAndLastDigestSentAtIsNull();
}
