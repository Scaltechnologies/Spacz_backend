package com.spacz.studyhall.service.support;

import com.spacz.studyhall.entity.OperatingHours;
import com.spacz.studyhall.entity.StudyHall;

import java.time.DayOfWeek;
import java.time.LocalTime;

public final class DefaultHours {

    public static final LocalTime OPEN = LocalTime.of(6, 0);
    public static final LocalTime CLOSE = LocalTime.of(22, 0);

    private DefaultHours() {
    }

    public static void seed(StudyHall hall, LocalTime open, LocalTime close) {
        for (DayOfWeek day : DayOfWeek.values()) {
            hall.getOperatingHours().add(OperatingHours.open(hall, day, open, close));
        }
    }
}
