package com.spacz.user.repository;

import com.spacz.user.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    Page<Feedback> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Used to absorb an accidental double submit of the same feedback. */
    Optional<Feedback> findFirstByUserIdAndRatingAndCommentAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, short rating, String comment, Instant after);

    Optional<Feedback> findFirstByUserIdAndRatingAndCommentIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
            Long userId, short rating, Instant after);
}
