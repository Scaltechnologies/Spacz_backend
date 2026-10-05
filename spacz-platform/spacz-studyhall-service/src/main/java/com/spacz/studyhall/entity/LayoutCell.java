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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * One occupied position of a block layout: a seat or a physical feature (walkway, entrance ...).
 * Positions without a cell are EMPTY, so gaps are never stored. A SEAT cell sits at its seat's
 * (row, column); PostgreSQL enforces that and the seat's block (V6).
 */
@Entity
@Table(name = "layout_cells", uniqueConstraints = {
        @UniqueConstraint(name = "uk_layout_cells_position", columnNames = {"layout_id", "row_index", "column_index"}),
        @UniqueConstraint(name = "uk_layout_cells_seat", columnNames = "seat_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "layout_id", nullable = false, updatable = false)
    private BlockLayout layout;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "cell_type", nullable = false, length = 20)
    private LayoutCellType type;

    /** Only for SEAT cells. Deleting the seat deletes its cell. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(length = 40)
    private String label;

    /** Facing / opening / airflow; only for {@link LayoutCellType#isDirectional()} types (V7). */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Direction direction;

    static LayoutCell seat(BlockLayout layout, Seat seat, Direction direction) {
        Direction.requireValidFor(LayoutCellType.SEAT, direction);
        LayoutCell cell = new LayoutCell();
        cell.direction = direction;
        cell.layout = layout;
        cell.rowIndex = seat.getRowIndex();
        cell.columnIndex = seat.getColumnIndex();
        cell.type = LayoutCellType.SEAT;
        cell.seat = seat;
        return cell;
    }

    static LayoutCell feature(BlockLayout layout, int row, int column, LayoutCellType type, String label,
                              Direction direction) {
        Direction.requireValidFor(type, direction);
        LayoutCell cell = new LayoutCell();
        cell.direction = direction;
        cell.layout = layout;
        cell.rowIndex = row;
        cell.columnIndex = column;
        cell.type = type;
        cell.label = label;
        return cell;
    }

    void orient(Direction newDirection) {
        Direction.requireValidFor(type, newDirection);
        direction = newDirection;
    }

    public boolean isSeat() {
        return type == LayoutCellType.SEAT;
    }

    public boolean isAt(int row, int column) {
        return rowIndex == row && columnIndex == column;
    }
}
