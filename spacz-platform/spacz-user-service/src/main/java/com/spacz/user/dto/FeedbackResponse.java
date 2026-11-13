package com.spacz.user.dto;

import com.spacz.user.entity.Feedback;

import java.time.Instant;

public record FeedbackResponse(Long id, int rating, String comment, Instant createdAt) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getRating(), feedback.getComment(), feedback.getCreatedAt());
    }
}
