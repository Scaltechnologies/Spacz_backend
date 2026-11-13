package com.spacz.studyhall.service.support;

import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.LayoutCellType;
import com.spacz.studyhall.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the frontend's layout template into cells. The template is only an input format: the result
 * is stored as structured layout cells and seats.
 * <pre>
 * s1 s2 s3 . . V:Entrance . . s4 s5     one line = one row, one token = one column
 * P:Walkway                             a line with only P:&lt;label&gt; or A = a full-width walkway row
 * </pre>
 * Tokens: {@code .} empty · {@code s}, {@code s12}, {@code s:A01} seat (positional number such as B3, 12, A01) ·
 * {@code X:Label} feature whose type is the label (Entrance, Table, Pillar ...) or else OTHER ·
 * {@code TABLE}, {@code PILLAR} ... a feature by type name. Any cell token may end with {@code @NORTH},
 * {@code @SOUTH}, {@code @EAST}, {@code @WEST} or {@code @CENTER} (or {@code @N/@S/@E/@W/@C}; legacy
 * {@code @U/@D/@L/@R} are accepted): the way a
 * seat faces, an entrance/exit opens or an AC blows (see {@link Direction}). Blank lines are ignored;
 * shorter rows are padded with empty cells. Line 1 is row 1 = row A, the entrance/user side.
 */
public final class LayoutTemplateParser {

    private static final Pattern SEAT = Pattern.compile("(?i)s(\\d{1,6})?|s:([A-Za-z0-9-]{1,20})");
    private static final int MAX_LABEL = 40;

    public record Cell(int row, int column, LayoutCellType type, String label, String seatNumber, Direction direction) {

        public Cell(int row, int column, LayoutCellType type, String label, String seatNumber) {
            this(row, column, type, label, seatNumber, null);
        }
    }

    public record Result(int rows, int columns, List<Cell> cells) {

        public long seatCount() {
            return cells.stream().filter(c -> c.type() == LayoutCellType.SEAT).count();
        }
    }

    private LayoutTemplateParser() {
    }

    public static Result parse(String template) {
        List<String[]> lines = new ArrayList<>();
        for (String line : template.split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line.trim().split("\\s+"));
            }
        }
        if (lines.isEmpty()) {
            throw invalid("The layout template is empty");
        }
        int columns = lines.stream().filter(t -> !isFullRow(t)).mapToInt(t -> t.length).max().orElse(1);
        int rows = lines.size();
        if (rows > Block.MAX_GRID || columns > Block.MAX_GRID) {
            throw invalid("A layout can have at most " + Block.MAX_GRID + " rows and " + Block.MAX_GRID + " columns");
        }

        List<Cell> cells = new ArrayList<>();
        Set<String> seatNumbers = new HashSet<>();
        for (int r = 0; r < rows; r++) {
            String[] tokens = lines.get(r);
            int row = r + 1;
            if (isFullRow(tokens)) {
                String label = tokens[0].equalsIgnoreCase("A") ? null : label(tokens[0].substring(2), row);
                LayoutCellType type = LayoutCellType.fromName(label).orElse(LayoutCellType.WALKWAY);
                for (int column = 1; column <= columns; column++) {
                    cells.add(new Cell(row, column, type, column == 1 ? label : null, null));
                }
                continue;
            }
            for (int c = 0; c < tokens.length; c++) {
                String token = tokens[c];
                int column = c + 1;
                if (token.equals(".")) {
                    continue;
                }
                Direction direction = null;
                int at = token.lastIndexOf('@');
                if (at > 0) {
                    String suffix = token.substring(at + 1);
                    direction = Direction.parse(suffix).orElseThrow(() -> invalid("Unknown direction '" + suffix
                            + "' at row " + row + ", column " + column + " (use NORTH, SOUTH, EAST, WEST or CENTER)"));
                    token = token.substring(0, at);
                }
                Matcher seat = SEAT.matcher(token);
                if (seat.matches()) {
                    String number;
                    if (seat.group(1) != null) {
                        number = String.valueOf(Integer.parseInt(seat.group(1)));
                    } else if (seat.group(2) != null) {
                        number = seat.group(2).toUpperCase(Locale.ROOT);
                    } else {
                        // Same convention as grid blocks: <row letter><column>, e.g. B3 (prefixed "A-B3").
                        number = SeatNumbering.rowLabel(row) + column;
                    }
                    if (!seatNumbers.add(number.toLowerCase(Locale.ROOT))) {
                        throw invalid("Seat " + number + " appears twice in the layout template (row " + row + ")");
                    }
                    cells.add(withDirection(new Cell(row, column, LayoutCellType.SEAT, null, number), direction));
                } else {
                    Cell feature = feature(token, row, column);
                    if (feature.type() != LayoutCellType.EMPTY) {
                        cells.add(withDirection(feature, direction));
                    }
                }
            }
        }
        return new Result(rows, columns, cells);
    }

    private static Cell withDirection(Cell cell, Direction direction) {
        if (direction == null) {
            return cell;
        }
        try {
            Direction.requireValidFor(cell.type(), direction);
        } catch (RuntimeException e) {
            throw invalid(e.getMessage() + " (row " + cell.row() + ", column " + cell.column() + ")");
        }
        return new Cell(cell.row(), cell.column(), cell.type(), cell.label(), cell.seatNumber(), direction);
    }

    private static boolean isFullRow(String[] tokens) {
        return tokens.length == 1 && (tokens[0].equalsIgnoreCase("A") || tokens[0].regionMatches(true, 0, "P:", 0, 2));
    }

    private static Cell feature(String token, int row, int column) {
        int colon = token.indexOf(':');
        if (colon < 0) {
            LayoutCellType type = LayoutCellType.fromName(token)
                    .filter(t -> t != LayoutCellType.SEAT)
                    .orElseThrow(() -> invalid("Unknown layout token '" + token + "' at row " + row + ", column " + column));
            return new Cell(row, column, type, null, null);
        }
        String label = label(token.substring(colon + 1), row);
        LayoutCellType type = LayoutCellType.fromName(label)
                .or(() -> LayoutCellType.fromName(token.substring(0, colon)))
                .filter(t -> t != LayoutCellType.SEAT)
                .orElse(LayoutCellType.OTHER);
        return new Cell(row, column, type, label, null);
    }

    private static String label(String raw, int row) {
        String label = raw.replace('_', ' ').trim();
        if (label.isEmpty() || label.length() > MAX_LABEL) {
            throw invalid("A layout label must have 1 to " + MAX_LABEL + " characters (row " + row + ")");
        }
        return label;
    }

    private static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("INVALID_LAYOUT_TEMPLATE", message);
    }
}
