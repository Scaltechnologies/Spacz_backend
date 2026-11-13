package com.spacz.user.service.impl;

import com.spacz.user.dto.FeedbackRequest;
import com.spacz.user.dto.FeedbackResponse;
import com.spacz.user.dto.PageResponse;
import com.spacz.user.entity.Feedback;
import com.spacz.user.repository.FeedbackRepository;
import com.spacz.user.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(1);

    private final FeedbackRepository feedbackRepository;

    @Override
    @Transactional
    public FeedbackResponse submit(Long userId, FeedbackRequest request) {
        short rating = request.rating().shortValue();
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        Instant since = Instant.now().minus(DUPLICATE_WINDOW);
        Optional<Feedback> duplicate = comment == null
                ? feedbackRepository.findFirstByUserIdAndRatingAndCommentIsNullAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, rating, since)
                : feedbackRepository.findFirstByUserIdAndRatingAndCommentAndCreatedAtAfterOrderByCreatedAtDesc(
                        userId, rating, comment, since);
        Feedback feedback = duplicate.orElseGet(() -> feedbackRepository.save(Feedback.create(userId, rating, comment)));
        return FeedbackResponse.from(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> mine(Long userId, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, unsorted),
                FeedbackResponse::from);
    }
}
