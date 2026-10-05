package com.spacz.studyhall.entity;

import com.spacz.studyhall.exception.BusinessRuleException;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A fixture (e.g. AC) annotated on a block layout: an edge or centre position plus an optional offset
 * along that edge (column for NORTH/SOUTH, row for EAST/WEST; null = centred) and a direction. It
 * occupies no grid cell (V8).
 */
@Entity
@Table(name = "layout_fixtures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutFixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "layout_id", nullable = false, updatable = false)
    private BlockLayout layout;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixture_type", nullable = false, length = 20)
    private FixtureType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge", nullable = false, length = 10)
    private FixturePosition position;

    /** 1-based column (NORTH/SOUTH) or row (EAST/WEST) along the edge; null = centred. */
    @Column(name = "position_offset")
    private Integer offset;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Direction direction;

    @Column(length = 40)
    private String label;

    /** What to place (validated by {@link #of}). */
    public record Spec(FixtureType type, FixturePosition position, Integer offset, Direction direction, String label) {
    }

    static LayoutFixture of(BlockLayout layout, FixtureType type, FixturePosition position, Integer offset,
                            Direction direction, String label) {
        if (type == null || position == null) {
            throw new BusinessRuleException("INVALID_LAYOUT_FIXTURE", "A fixture needs a type and a position");
        }
        if (offset != null) {
            int max = switch (position) {
                case NORTH, SOUTH -> layout.getColumns();
                case EAST, WEST -> layout.getRows();
                case CENTER -> 0;
            };
            if (offset < 1 || offset > max) {
                throw new BusinessRuleException("INVALID_LAYOUT_FIXTURE", position == FixturePosition.CENTER
                        ? "A CENTER fixture has no offset"
                        : "Fixture offset " + offset + " is outside the " + position + " edge (1-" + max + ")");
            }
        }
        LayoutFixture fixture = new LayoutFixture();
        fixture.layout = layout;
        fixture.type = type;
        fixture.position = position;
        fixture.offset = offset;
        fixture.direction = direction;
        fixture.label = label;
        return fixture;
    }

    /** Still on the grid after a resize (edge offsets must stay inside it). */
    boolean fits(int rows, int columns) {
        return offset == null || switch (position) {
            case NORTH, SOUTH -> offset <= columns;
            case EAST, WEST -> offset <= rows;
            case CENTER -> true;
        };
    }
}
