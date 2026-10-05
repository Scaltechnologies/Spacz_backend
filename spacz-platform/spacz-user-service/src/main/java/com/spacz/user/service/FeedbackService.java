package com.spacz.user.service;

import com.spacz.user.dto.FeedbackRequest;
import com.spacz.user.dto.FeedbackResponse;
import com.spacz.user.dto.PageResponse;
import org.springframework.data.domain.Pageable;

public interface FeedbackService {

    /** Saves feedback; an identical submission within a minute returns the existing one instead. */
    FeedbackResponse submit(Long userId, FeedbackRequest request);

    PageResponse<FeedbackResponse> mine(Long userId, Pageable pageable);
}
