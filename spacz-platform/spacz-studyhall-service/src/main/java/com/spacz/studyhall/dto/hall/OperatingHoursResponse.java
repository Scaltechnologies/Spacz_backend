package com.spacz.studyhall.dto.hall;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OperatingHoursResponse(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
}
