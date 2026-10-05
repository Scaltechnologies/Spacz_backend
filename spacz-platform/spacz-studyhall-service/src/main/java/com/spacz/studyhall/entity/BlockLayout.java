package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.DuplicateResourceException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The physical layout of a block: a rows × columns grid whose occupied positions are
 * {@link LayoutCell}s (seats and features). It shares the block's primary key and is loaded through
 * {@code BlockLayoutRepository} (the block does not map it, so loading blocks never loads layouts).
 * Booking state is never stored here; it is combined with the cells at response time.
 */
@Entity
@Table(name = "block_layouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockLayout {

    @Id
    @Column(name = "block_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "block_id")
    private Block block;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout_type", nullable = false, length = 20)
    private LayoutType layoutType;

    @Column(name = "row_count", nullable = false)
    private int rows;

    @Column(name = "column_count", nullable = false)
    private int columns;

    @Version
    @Column(name = "layout_version", nullable = false)
    private Long layoutVersion;

    @OrderBy("rowIndex ASC, columnIndex ASC")
    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LayoutCell> cells = new ArrayList<>();

    /** Edge / centre fixtures (AC ...); they take no cell (V8). */
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LayoutFixture> fixtures = new ArrayList<>();

    /** A layout sized like the block, with a SEAT cell for each seat the block already has. */
    public static BlockLayout of(Block block, LayoutType type) {
        BlockLayout layout = new BlockLayout();
        layout.block = block;
        layout.layoutType = type;
        layout.rows = block.getTotalRows();
        layout.columns = block.getTotalColumns();
        block.getSeats().forEach(seat -> layout.cells.add(LayoutCell.seat(layout, seat, null)));
        return layout;
    }

    public boolean contains(int row, int column) {
        return row >= 1 && row <= rows && column >= 1 && column <= columns;
    }

    public Optional<LayoutCell> cellAt(int row, int column) {
        return cells.stream().filter(c -> c.isAt(row, column)).findFirst();
    }

    /** Places a new seat of this block at its (row, column), which must be inside the grid and free. */
    public void addSeat(Seat seat) {
        addSeat(seat, null);
    }

    /** Like {@link #addSeat(Seat)}, with the way the seat faces (null = unspecified). */
    public void addSeat(Seat seat, Direction direction) {
        if (seat.getBlock() != block) {
            throw new BusinessRuleException("Seat " + seat.getSeatNumber() + " belongs to another block");
        }
        requireFree(seat.getRowIndex(), seat.getColumnIndex());
        if (!block.getSeats().contains(seat)) {
            block.getSeats().add(seat);
        }
        cells.add(LayoutCell.seat(this, seat, direction));
    }

    /**
     * Removes the seat's cell (the position becomes EMPTY); the seat itself is untouched. Deleting a seat
     * needs no call: its cell is deleted with it (ON DELETE CASCADE).
     */
    public void removeSeatCell(Seat seat) {
        cells.removeIf(c -> c.getSeat() == seat);
    }

    public void addFeature(int row, int column, LayoutCellType type, String label) {
        addFeature(row, column, type, label, null);
    }

    public void addFeature(int row, int column, LayoutCellType type, String label, Direction direction) {
        if (type == LayoutCellType.SEAT || type == LayoutCellType.EMPTY) {
            throw new BusinessRuleException("INVALID_LAYOUT_CELL", type + " is not a layout feature");
        }
        requireFree(row, column);
        cells.add(LayoutCell.feature(this, row, column, type, label, direction));
        layoutType = LayoutType.CUSTOM;
    }

    /** Sets the way the seat at (row, column) faces; null clears it. */
    public void orientSeat(int row, int column, Direction direction) {
        LayoutCell cell = cellAt(row, column).filter(LayoutCell::isSeat)
                .orElseThrow(() -> new BusinessRuleException("INVALID_LAYOUT_CELL",
                        "There is no seat at (" + row + "," + column + ")"));
        cell.orient(direction);
    }

    /** Replaces all fixtures (each is validated against the current grid size). */
    public void replaceFixtures(List<LayoutFixture.Spec> specs) {
        fixtures.clear();
        specs.forEach(s -> fixtures.add(LayoutFixture.of(this, s.type(), s.position(), s.offset(), s.direction(), s.label())));
    }

    /** Removes every non-seat cell. Flush before placing features at the same positions again. */
    public void clearFeatures() {
        cells.removeIf(c -> !c.isSeat());
    }

    /** Changes the grid size; it can shrink only when no cell lies outside the new size. */
    public void resize(int newRows, int newColumns) {
        if (newRows < 1 || newColumns < 1 || newRows > Block.MAX_GRID || newColumns > Block.MAX_GRID) {
            throw new BusinessRuleException("A layout must be between 1x1 and " + Block.MAX_GRID + "x" + Block.MAX_GRID);
        }
        boolean outside = cells.stream().anyMatch(c -> c.getRowIndex() > newRows || c.getColumnIndex() > newColumns);
        if (outside) {
            throw new BusinessRuleException("Seats or layout features exist outside a " + newRows + "x" + newColumns
                    + " grid; move or remove them first");
        }
        if (fixtures.stream().anyMatch(f -> !f.fits(newRows, newColumns))) {
            throw new BusinessRuleException("A fixture is placed outside a " + newRows + "x" + newColumns
                    + " grid; move or remove it first");
        }
        rows = newRows;
        columns = newColumns;
        block.syncSize(newRows, newColumns);
    }

    /**
     * The first free position in row-major order, growing the grid by one row when it is full. Used by
     * the legacy API and the data migration, whose seats have no position.
     */
    public Optional<int[]> nextFreeCell() {
        Set<Long> taken = new HashSet<>();
        cells.forEach(c -> taken.add(key(c.getRowIndex(), c.getColumnIndex())));
        for (int row = 1; row <= Block.MAX_GRID; row++) {
            if (row > rows) {
                resize(row, columns);
            }
            for (int column = 1; column <= columns; column++) {
                if (!taken.contains(key(row, column))) {
                    return Optional.of(new int[]{row, column});
                }
            }
        }
        return Optional.empty();
    }

    /** Fails unless (row, column) is inside the grid and has no seat or feature. */
    public void requireFree(int row, int column) {
        if (!contains(row, column)) {
            throw new BusinessRuleException("Position (" + row + "," + column + ") is outside the "
                    + rows + "x" + columns + " grid");
        }
        cellAt(row, column).ifPresent(existing -> {
            throw new DuplicateResourceException(existing.isSeat()
                    ? "There is already a seat at (" + row + "," + column + ")"
                    : "Position (" + row + "," + column + ") is taken by " + existing.getType());
        });
    }

    private static long key(int row, int column) {
        return (long) row << 32 | column;
    }
}
