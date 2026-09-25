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
import java.util.Optional;
import java.util.Set;

/**
 * A section of a study hall (floor, AC room, silent room ...) laid out as a rows × columns grid.
 * Cells without a seat are gaps. A block may override the hall's prices and add its own amenities.
 * Legacy: the {@code block} table (+ its 1:1 {@code amenity} row).
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

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

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

    public static Block of(StudyHall hall, String name, int totalRows, int totalColumns, int displayOrder) {
        Block block = new Block();
        block.studyHall = hall;
        block.name = name;
        block.totalRows = totalRows;
        block.totalColumns = totalColumns;
        block.displayOrder = displayOrder;
        return block;
    }

    public boolean contains(int row, int column) {
        return row >= 1 && row <= totalRows && column >= 1 && column <= totalColumns;
    }

    public boolean isOccupied(int row, int column) {
        return seats.stream().anyMatch(s -> s.getRowIndex() == row && s.getColumnIndex() == column);
    }

    /**
     * The first free cell in row-major order, growing the grid by one row when it is full. Used by
     * the legacy API and the data migration, whose seats have no position.
     */
    public Optional<int[]> nextFreeCell() {
        for (int row = 1; row <= MAX_GRID; row++) {
            if (row > totalRows) {
                totalRows = row;
            }
            for (int column = 1; column <= totalColumns; column++) {
                if (!isOccupied(row, column)) {
                    return Optional.of(new int[]{row, column});
                }
            }
        }
        return Optional.empty();
    }

    public BigDecimal effectiveDailyPrice() {
        return dailyPrice != null ? dailyPrice : studyHall.getPricePerDay();
    }

    public BigDecimal effectiveMonthlyPrice() {
        return monthlyPrice != null ? monthlyPrice : studyHall.getPricePerMonth();
    }
}
