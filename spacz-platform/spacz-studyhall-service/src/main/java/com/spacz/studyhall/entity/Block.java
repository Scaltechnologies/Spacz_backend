package com.spacz.studyhall.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A section of a floor (AC room, silent room ...). Its physical arrangement is its {@link BlockLayout}
 * (same id, loaded separately). A block may override the hall's prices and add its own amenities.
 * The hall reference is kept next to the floor for the hall-scoped queries; PostgreSQL enforces
 * that both agree (V6). Legacy: the {@code block} table (+ its 1:1 {@code amenity} row).
 */
@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    public static final int MAX_GRID = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false)
    private StudyHall studyHall;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(nullable = false, length = 80)
    private String name;

    /** Mirrors the layout size (kept for existing responses); change it with {@link BlockLayout#resize}. */
    @Setter(AccessLevel.NONE)
    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Setter(AccessLevel.NONE)
    @Column(name = "total_columns", nullable = false)
    private int totalColumns;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "daily_price", precision = 10, scale = 2)
    private BigDecimal dailyPrice;

    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "legacy_block_id", unique = true)
    private Long legacyBlockId;

    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(name = "block_amenities",
            joinColumns = @JoinColumn(name = "block_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new LinkedHashSet<>();

    @BatchSize(size = 50)
    @OrderBy("rowIndex ASC, columnIndex ASC")
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    public static Block of(Floor floor, String name, int totalRows, int totalColumns, int displayOrder) {
        Block block = new Block();
        block.studyHall = floor.getStudyHall();
        block.floor = floor;
        block.name = name;
        block.totalRows = totalRows;
        block.totalColumns = totalColumns;
        block.displayOrder = displayOrder;
        return block;
    }

    /** Moves the block (with its seats) to a floor of the same or another hall of the vendor. */
    public void moveTo(Floor target) {
        this.floor = target;
        this.studyHall = target.getStudyHall();
        seats.forEach(seat -> seat.moveTo(this, seat.getRowIndex(), seat.getColumnIndex()));
    }

    void syncSize(int rows, int columns) {
        this.totalRows = rows;
        this.totalColumns = columns;
    }

    public BigDecimal effectiveDailyPrice() {
        return dailyPrice != null ? dailyPrice : studyHall.getPricePerDay();
    }

    public BigDecimal effectiveMonthlyPrice() {
        return monthlyPrice != null ? monthlyPrice : studyHall.getPricePerMonth();
    }
}
