package com.spacz.studyhall.service.support;

import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.LayoutCellType;
import com.spacz.studyhall.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LayoutTemplateParserTest {

    @Test
    void parsesSeatsGapsFeaturesAndFullWidthRows() {
        LayoutTemplateParser.Result result = LayoutTemplateParser.parse("""
                s1 s2 . V:Entrance . s3

                P:Walkway
                s:B01 s . TABLE PILLAR
                A
                """);

        assertThat(result.rows()).isEqualTo(4);
        assertThat(result.columns()).isEqualTo(6);
        assertThat(result.seatCount()).isEqualTo(5);
        assertThat(at(result, 1, 4)).extracting(LayoutTemplateParser.Cell::type, LayoutTemplateParser.Cell::label)
                .containsExactly(LayoutCellType.ENTRANCE, "Entrance");
        assertThat(at(result, 1, 6).seatNumber()).isEqualTo("3");
        assertThat(result.cells()).filteredOn(c -> c.row() == 2)
                .hasSize(6).allMatch(c -> c.type() == LayoutCellType.WALKWAY);
        assertThat(at(result, 3, 1).seatNumber()).isEqualTo("B01");
        assertThat(at(result, 3, 2).seatNumber()).isEqualTo("C2");
        assertThat(at(result, 3, 4).type()).isEqualTo(LayoutCellType.TABLE);
        assertThat(at(result, 3, 5).type()).isEqualTo(LayoutCellType.PILLAR);
        assertThat(result.cells()).filteredOn(c -> c.row() == 4).hasSize(6);
        assertThat(result.cells()).noneMatch(c -> c.row() == 1 && c.column() == 3);
    }

    @Test
    void unknownFeatureLabelsBecomeOther() {
        LayoutTemplateParser.Cell cell = LayoutTemplateParser.parse("s1 X:Water_Cooler").cells().get(1);
        assertThat(cell.type()).isEqualTo(LayoutCellType.OTHER);
        assertThat(cell.label()).isEqualTo("Water Cooler");
    }

    @Test
    void rejectsDuplicateSeatsUnknownTokensAndOversizedGrids() {
        assertThatThrownBy(() -> LayoutTemplateParser.parse("s1 s1")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LayoutTemplateParser.parse("s1 ??")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LayoutTemplateParser.parse("  \n ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LayoutTemplateParser.parse("s ".repeat(61))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void parsesDirectionsAndAcFixtures() {
        // row A (entrance side): seats facing up around an entrance opening down; row B: table with seats facing it
        LayoutTemplateParser.Result result = LayoutTemplateParser.parse("""
                s@U s@UP ENTRANCE:Main_Entrance@DOWN s@R
                s@D TABLE s@L AC@C
                . AC@LEFT . .
                """);

        assertThat(result.seatCount()).isEqualTo(5);
        assertThat(at(result, 1, 1).direction()).isEqualTo(Direction.NORTH);
        assertThat(at(result, 1, 2).direction()).isEqualTo(Direction.NORTH);
        assertThat(at(result, 1, 3)).extracting(LayoutTemplateParser.Cell::type, LayoutTemplateParser.Cell::label,
                LayoutTemplateParser.Cell::direction).containsExactly(LayoutCellType.ENTRANCE, "Main Entrance", Direction.SOUTH);
        assertThat(at(result, 1, 4).direction()).isEqualTo(Direction.EAST);
        assertThat(at(result, 2, 1).direction()).isEqualTo(Direction.SOUTH);
        assertThat(at(result, 2, 2)).extracting(LayoutTemplateParser.Cell::type, LayoutTemplateParser.Cell::direction)
                .containsExactly(LayoutCellType.TABLE, null);
        assertThat(at(result, 2, 4)).extracting(LayoutTemplateParser.Cell::type, LayoutTemplateParser.Cell::direction)
                .containsExactly(LayoutCellType.AC, Direction.CENTER);
        assertThat(at(result, 3, 2).direction()).isEqualTo(Direction.WEST);
        assertThat(at(result, 1, 1).seatNumber()).isEqualTo("A1");
    }

    @Test
    void compassTokensAndLegacyAliasesAreEquivalent() {
        LayoutTemplateParser.Result result = LayoutTemplateParser.parse("s@N s@NORTH s@U s@E s@W s@S EXIT@SOUTH");
        assertThat(result.cells()).extracting(LayoutTemplateParser.Cell::direction).containsExactly(
                Direction.NORTH, Direction.NORTH, Direction.NORTH, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.SOUTH);
    }

    @Test
    void rejectsDirectionsThatMakeNoSense() {
        assertThatThrownBy(() -> LayoutTemplateParser.parse("TABLE@UP")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LayoutTemplateParser.parse("s@CENTER")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> LayoutTemplateParser.parse("s@SIDEWAYS")).isInstanceOf(BusinessRuleException.class);
    }

    private static LayoutTemplateParser.Cell at(LayoutTemplateParser.Result result, int row, int column) {
        return result.cells().stream().filter(c -> c.row() == row && c.column() == column).findFirst().orElseThrow();
    }
}
