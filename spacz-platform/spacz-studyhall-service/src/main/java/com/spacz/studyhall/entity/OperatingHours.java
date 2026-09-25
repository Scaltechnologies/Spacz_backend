package com.spacz.studyhall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Opening hours of a study hall for one weekday. The single source of truth for opening times.
 */
@Entity
@Table(name = "study_hall_operating_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false, updatable = false)
    private StudyHall studyHall;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(nullable = false)
    private boolean closed;

    public static OperatingHours open(StudyHall hall, DayOfWeek day, LocalTime open, LocalTime close) {
        OperatingHours hours = new OperatingHours();
        hours.studyHall = hall;
        hours.dayOfWeek = day;
        hours.openTime = open;
        hours.closeTime = close;
        hours.closed = false;
        return hours;
    }

    public static OperatingHours closed(StudyHall hall, DayOfWeek day) {
        OperatingHours hours = new OperatingHours();
        hours.studyHall = hall;
        hours.dayOfWeek = day;
        hours.closed = true;
        return hours;
    }

    public void update(LocalTime open, LocalTime close, boolean isClosed) {
        this.openTime = isClosed ? null : open;
        this.closeTime = isClosed ? null : close;
        this.closed = isClosed;
    }
}
