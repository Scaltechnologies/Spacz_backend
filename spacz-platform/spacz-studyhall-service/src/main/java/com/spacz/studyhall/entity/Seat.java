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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One seat at a (row, column) cell of a block. The hall reference is denormalised for fast
 * availability queries. Price overrides are optional (seat → block → hall).
 */
@Entity
@Table(name = "seats")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false)
    private StudyHall studyHall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "block_id", nullable = false)
    private Block block;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, length = 20)
    private SeatType seatType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "price_per_day", precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(name = "price_per_month", precision = 10, scale = 2)
    private BigDecimal pricePerMonth;

    @Column(name = "legacy_seat_id", unique = true)
    private Long legacySeatId;

    @Version
    private Long version;

    public static Seat of(Block block, String seatNumber, int row, int column, SeatType type) {
        Seat seat = new Seat();
        seat.studyHall = block.getStudyHall();
        seat.block = block;
        seat.seatNumber = seatNumber;
        seat.rowIndex = row;
        seat.columnIndex = column;
        seat.seatType = type;
        seat.status = SeatStatus.AVAILABLE;
        return seat;
    }

    /** Moves the seat to a cell of another block (same or other hall of the same vendor). */
    public void moveTo(Block target, int row, int column) {
        this.block = target;
        this.studyHall = target.getStudyHall();
        this.rowIndex = row;
        this.columnIndex = column;
    }

    public BigDecimal effectiveDailyPrice() {
        return pricePerDay != null ? pricePerDay : block.effectiveDailyPrice();
    }

    public BigDecimal effectiveMonthlyPrice() {
        return pricePerMonth != null ? pricePerMonth : block.effectiveMonthlyPrice();
    }

    public BigDecimal effectivePrice(BookingPlan plan) {
        return plan == BookingPlan.MONTHLY ? effectiveMonthlyPrice() : effectiveDailyPrice();
    }
}
