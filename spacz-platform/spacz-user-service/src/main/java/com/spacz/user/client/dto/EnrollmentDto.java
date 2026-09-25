package com.spacz.user.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

/**
 * A hall membership as returned by studyhall-service ({@code program} passed through).
 */
public record EnrollmentDto(Long id, Long studyHallId, String studyHallName, JsonNode program, Long seatId,
                            String seatNumber, String plan, LocalDate startDate, LocalDate endDate, String status,
                            String source) {
}
