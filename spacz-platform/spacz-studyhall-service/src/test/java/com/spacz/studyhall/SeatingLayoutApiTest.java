package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Floors → blocks → layout cells → seats through the HTTP API (H2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeatingLayoutApiTest extends ApiTestSupport {

    private static final String TEMPLATE = """
            s1 s2 s3 s4 s5 . . V:Entrance . . s6 s7 s8 s9 s10

            P:Walkway

            s11 s12 s13 s14 s15 . . . . s16 s17 s18 s19 s20

            s21 s22 s23 s24 s25 . . . . s26 s27 s28 s29 s30

            A

            s31 s32 s33 s34 s35 . . . . s36 s37 s38 s39 s40
            """;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void floorsBlocksAndLayoutCells() throws Exception {
        long vendorId = 610;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Layout Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}""")
                .andExpect(status().isCreated()), "id");

        // Floors: unique numbers per hall.
        long floorId = id(send(post("/api/studyhalls/{id}/floors", hallId), vendor,
                "{\"floorNumber\":1,\"name\":\"First Floor\"}").andExpect(status().isCreated()), "id");
        send(post("/api/studyhalls/{id}/floors", hallId), vendor, "{\"floorNumber\":1,\"name\":\"Again\"}")
                .andExpect(status().isConflict());

        // A block from the frontend template: 40 real seats, no fake seats for walkways and gaps.
        String body = JsonNodeFactory.instance.objectNode()
                .put("name", "Block A").put("seatNumberPrefix", "A").put("floorId", floorId)
                .put("layoutTemplate", TEMPLATE).toString();
        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floorId").value(floorId))
                .andExpect(jsonPath("$.totalRows").value(6))
                .andExpect(jsonPath("$.totalColumns").value(15))
                .andExpect(jsonPath("$.seats.length()").value(40))
                .andExpect(jsonPath("$.seats[0].seatNumber").value("A-1")));
        long blockId = block.path("id").asLong();

        JsonNode layout = json(send(get("/api/studyhalls/{id}/layout", hallId), vendor, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floors.length()").value(1))
                .andExpect(jsonPath("$.floors[0].name").value("First Floor")))
                .path("floors").get(0).path("blocks").get(0).path("layout");
        assertThat(layout.path("layoutType").asText()).isEqualTo("CUSTOM");
        JsonNode cells = layout.path("cells");
        assertThat(cells).hasSize(6 * 15);
        assertThat(cell(cells, 1, 1).path("type").asText()).isEqualTo("SEAT");
        assertThat(cell(cells, 1, 1).path("seatNumber").asText()).isEqualTo("A-1");
        assertThat(cell(cells, 1, 1).path("available").asBoolean()).isTrue();
        assertThat(cell(cells, 1, 6).path("type").asText()).isEqualTo("EMPTY");
        assertThat(cell(cells, 1, 6).has("seatId")).isFalse();
        assertThat(cell(cells, 1, 8).path("type").asText()).isEqualTo("ENTRANCE");
        assertThat(cell(cells, 1, 8).path("label").asText()).isEqualTo("Entrance");
        assertThat(cell(cells, 1, 11).path("seatNumber").asText()).isEqualTo("A-6");
        assertThat(cell(cells, 2, 1).path("type").asText()).isEqualTo("WALKWAY");
        assertThat(cell(cells, 2, 15).path("type").asText()).isEqualTo("WALKWAY");
        assertThat(cell(cells, 3, 15).path("type").asText()).isEqualTo("EMPTY");
        assertThat(cell(cells, 5, 7).path("type").asText()).isEqualTo("WALKWAY");

        // Features: replaced as a whole; they never overlap seats.
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor,
                "{\"features\":[{\"row\":1,\"column\":1,\"type\":\"TABLE\"}]}")
                .andExpect(status().isConflict());
        JsonNode updated = json(send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor, """
                {"features":[{"row":1,"column":8,"type":"EXIT","label":"Exit"},{"row":3,"column":7,"type":"PILLAR"}]}""")
                .andExpect(status().isOk()));
        assertThat(cell(updated.path("layout").path("cells"), 1, 8).path("type").asText()).isEqualTo("EXIT");
        assertThat(cell(updated.path("layout").path("cells"), 2, 1).path("type").asText()).isEqualTo("EMPTY");

        // Seats cannot be placed on a feature; a deleted seat leaves an EMPTY position.
        send(post("/api/studyhalls/{id}/seats", hallId), vendor, """
                {"blockId":%d,"seatNumber":"X1","row":3,"column":7,"seatType":"STANDARD"}""".formatted(blockId))
                .andExpect(status().isConflict());
        send(post("/api/studyhalls/{id}/seats", hallId), vendor, """
                {"blockId":%d,"seatNumber":"X1","row":3,"column":8,"seatType":"STANDARD"}""".formatted(blockId))
                .andExpect(status().isCreated());
        long seatA1 = block.path("seats").get(0).path("id").asLong();
        send(delete("/api/studyhalls/{id}/seats/{s}", hallId, seatA1), vendor, null).andExpect(status().isNoContent());
        JsonNode after = json(send(get("/api/studyhalls/{id}/layout", hallId), vendor, null))
                .path("floors").get(0).path("blocks").get(0).path("layout").path("cells");
        assertThat(cell(after, 1, 1).path("type").asText()).isEqualTo("EMPTY");
        assertThat(cell(after, 3, 8).path("seatNumber").asText()).isEqualTo("X1");
        assertThat(cell(after, 3, 7).path("type").asText()).isEqualTo("PILLAR");

        // Shrinking the grid over a feature and deleting a floor with blocks are refused.
        send(put("/api/studyhalls/{id}/blocks/{b}", hallId, blockId), vendor,
                "{\"name\":\"Block A\",\"totalRows\":2,\"totalColumns\":15}")
                .andExpect(status().isUnprocessableEntity());
        send(delete("/api/studyhalls/{id}/floors/{f}", hallId, floorId), vendor, null).andExpect(status().isConflict());

        // A grid block without a floor goes to the hall's first floor (display order, then number); it can move.
        long groundId = id(send(post("/api/studyhalls/{id}/floors", hallId), vendor,
                "{\"floorNumber\":0,\"name\":\"Ground Floor\",\"displayOrder\":0}").andExpect(status().isCreated()), "id");
        long gridBlock = id(send(post("/api/studyhalls/{id}/blocks", hallId), vendor, """
                {"name":"Grid","totalRows":2,"totalColumns":2,"seatNumberPrefix":"G","gaps":[{"row":1,"column":2}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floorId").value(groundId))
                .andExpect(jsonPath("$.seats.length()").value(3)), "id");
        send(get("/api/studyhalls/{id}/layout", hallId), vendor, null)
                .andExpect(jsonPath("$.floors.length()").value(2))
                .andExpect(jsonPath("$.floors[0].name").value("Ground Floor"))
                .andExpect(jsonPath("$.floors[0].blocks[0].layout.layoutType").value("GRID"))
                .andExpect(jsonPath("$.floors[0].blocks[0].layout.cells.length()").value(4))
                .andExpect(jsonPath("$.floors[0].blocks[0].layout.cells[1].type").value("EMPTY"));
        send(put("/api/studyhalls/{id}/blocks/{b}", hallId, gridBlock), vendor, """
                {"name":"Grid","totalRows":2,"totalColumns":2,"floorId":%d}""".formatted(floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floorId").value(floorId));
        send(get("/api/studyhalls/{id}/layout", hallId), vendor, null)
                .andExpect(jsonPath("$.floors[0].blocks.length()").value(0))
                .andExpect(jsonPath("$.floors[1].blocks.length()").value(2));
        send(delete("/api/studyhalls/{id}/floors/{f}", hallId, groundId), vendor, null).andExpect(status().isNoContent());

        // Draft halls are private.
        mvc.perform(get("/api/studyhalls/{id}/layout", hallId)).andExpect(status().isNotFound());
    }

    @Test
    void invalidTemplatesAreRejected() throws Exception {
        long vendorId = 611;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Bad Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}"""), "id");
        for (String template : new String[]{"s1 s1", "s1 ??", ". . ."}) {
            String body = JsonNodeFactory.instance.objectNode().put("name", "B").put("layoutTemplate", template).toString();
            send(post("/api/studyhalls/{id}/blocks", hallId), vendor, body).andExpect(status().isUnprocessableEntity());
        }
        send(post("/api/studyhalls/{id}/blocks", hallId), vendor, "{\"name\":\"No size\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void directionsAcAndPositionsArePersisted() throws Exception {
        long vendorId = 612;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Oriented Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}"""), "id");
        // Row A (entrance side): seats facing up, entrance opening down; row B: seats facing a table; AC above.
        String template = "s@U s@U ENTRANCE@D s@U\ns@R TABLE s@L .\n. AC@D . AC@C";
        String body = JsonNodeFactory.instance.objectNode().put("name", "Block A").put("seatNumberPrefix", "O")
                .put("layoutTemplate", template).toString();
        long blockId = id(send(post("/api/studyhalls/{id}/blocks", hallId), vendor, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(5)), "id");

        JsonNode cells = layoutCells(hallId, vendor);
        assertThat(cell(cells, 1, 1).path("direction").asText()).isEqualTo("NORTH");
        assertThat(cell(cells, 1, 1).path("position").asText()).isEqualTo("A1");
        assertThat(cell(cells, 1, 3).path("type").asText()).isEqualTo("ENTRANCE");
        assertThat(cell(cells, 1, 3).path("direction").asText()).isEqualTo("SOUTH");
        assertThat(cell(cells, 2, 1).path("direction").asText()).isEqualTo("EAST");
        assertThat(cell(cells, 2, 2).has("direction")).isFalse();
        assertThat(cell(cells, 3, 2).path("type").asText()).isEqualTo("AC");
        assertThat(cell(cells, 3, 4).path("direction").asText()).isEqualTo("CENTER");
        assertThat(cell(cells, 3, 4).path("position").asText()).isEqualTo("C4");

        // Features keep their direction; listed seats are re-oriented, others keep theirs.
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor, """
                {"features":[{"row":1,"column":3,"type":"ENTRANCE","direction":"LEFT"},{"row":3,"column":1,"type":"AC","direction":"RIGHT"}],
                 "seatDirections":[{"row":1,"column":1,"direction":"LEFT"},{"row":2,"column":3,"direction":null}]}""")
                .andExpect(status().isOk());
        cells = layoutCells(hallId, vendor);
        assertThat(cell(cells, 1, 3).path("direction").asText()).isEqualTo("WEST");
        assertThat(cell(cells, 3, 1).path("direction").asText()).isEqualTo("EAST");
        assertThat(cell(cells, 3, 2).path("type").asText()).isEqualTo("EMPTY");
        assertThat(cell(cells, 1, 1).path("direction").asText()).isEqualTo("WEST");
        assertThat(cell(cells, 1, 2).path("direction").asText()).isEqualTo("NORTH");
        assertThat(cell(cells, 2, 3).has("direction")).isFalse();

        // A new seat can face a direction; nonsense directions are rejected.
        send(post("/api/studyhalls/{id}/seats", hallId), vendor, """
                {"blockId":%d,"seatNumber":"O-9","row":2,"column":4,"seatType":"STANDARD","direction":"DOWN"}"""
                .formatted(blockId)).andExpect(status().isCreated());
        assertThat(cell(layoutCells(hallId, vendor), 2, 4).path("direction").asText()).isEqualTo("SOUTH");
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor,
                "{\"features\":[{\"row\":3,\"column\":2,\"type\":\"TABLE\",\"direction\":\"UP\"}]}")
                .andExpect(status().isUnprocessableEntity());
        String bad = JsonNodeFactory.instance.objectNode().put("name", "Bad").put("layoutTemplate", "s@CENTER").toString();
        send(post("/api/studyhalls/{id}/blocks", hallId), vendor, bad).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void seatMovesFixturesAndCompassDirections() throws Exception {
        long vendorId = 613;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Moves Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}"""), "id");
        // 2 rows: A (entrance side) facing north, B facing south; compass tokens
        String body = JsonNodeFactory.instance.objectNode().put("name", "Block M").put("seatNumberPrefix", "M")
                .put("layoutTemplate", "s@N s@N ENTRANCE@S\ns@S s@S .").toString();
        JsonNode block = json(send(post("/api/studyhalls/{id}/blocks", hallId), vendor, body).andExpect(status().isCreated()));
        long blockId = block.path("id").asLong();
        JsonNode cells = layoutCells(hallId, vendor);
        long a1 = cell(cells, 1, 1).path("seatId").asLong();
        long a2 = cell(cells, 1, 2).path("seatId").asLong();
        long b1 = cell(cells, 2, 1).path("seatId").asLong();
        long b2 = cell(cells, 2, 2).path("seatId").asLong();
        assertThat(cell(cells, 1, 1).path("direction").asText()).isEqualTo("NORTH");
        assertThat(cell(cells, 1, 3).path("direction").asText()).isEqualTo("SOUTH");

        // Insert a row at A: grow to 3 rows, then every seat moves one row back (ids and numbers kept).
        send(put("/api/studyhalls/{id}/blocks/{b}", hallId, blockId), vendor,
                "{\"name\":\"Block M\",\"totalRows\":3,\"totalColumns\":3}").andExpect(status().isOk());
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor, """
                {"features":[{"row":1,"column":3,"type":"ENTRANCE","direction":"SOUTH"}],
                 "seatMoves":[{"seatId":%d,"row":2,"column":1},{"seatId":%d,"row":2,"column":2},
                              {"seatId":%d,"row":3,"column":1},{"seatId":%d,"row":3,"column":2}],
                 "fixtures":[{"type":"AC","position":"NORTH","offset":2,"direction":"SOUTH","label":"Split AC"},
                             {"type":"AC","position":"CENTER","direction":"CENTER"}]}"""
                .formatted(a1, a2, b1, b2)).andExpect(status().isOk());
        JsonNode after = json(send(get("/api/studyhalls/{id}/layout", hallId), vendor, null))
                .path("floors").get(0).path("blocks").get(0).path("layout");
        cells = after.path("cells");
        assertThat(cell(cells, 1, 1).path("type").asText()).isEqualTo("EMPTY");
        assertThat(cell(cells, 2, 1).path("seatId").asLong()).isEqualTo(a1);
        assertThat(cell(cells, 2, 1).path("seatNumber").asText()).isEqualTo("M-A1");
        assertThat(cell(cells, 2, 1).path("position").asText()).isEqualTo("B1");
        assertThat(cell(cells, 2, 1).path("direction").asText()).isEqualTo("NORTH");
        assertThat(cell(cells, 3, 2).path("seatId").asLong()).isEqualTo(b2);
        assertThat(cell(cells, 3, 2).path("direction").asText()).isEqualTo("SOUTH");
        assertThat(after.path("fixtures")).hasSize(2);
        assertThat(after.path("fixtures").get(0).path("position").asText()).isEqualTo("NORTH");
        assertThat(after.path("fixtures").get(0).path("offset").asInt()).isEqualTo(2);
        assertThat(after.path("fixtures").get(1).path("direction").asText()).isEqualTo("CENTER");
        // /seats reflects the moved positions and still has 4 seats
        send(get("/api/studyhalls/{id}/seats", hallId), vendor, null)
                .andExpect(jsonPath("$.blocks[0].seats.length()").value(4));

        // Swapping two seats in one request works; fixtures are kept when omitted.
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor, """
                {"features":[],"seatMoves":[{"seatId":%d,"row":2,"column":2},{"seatId":%d,"row":2,"column":1}]}"""
                .formatted(a1, a2)).andExpect(status().isOk());
        after = json(send(get("/api/studyhalls/{id}/layout", hallId), vendor, null))
                .path("floors").get(0).path("blocks").get(0).path("layout");
        assertThat(cell(after.path("cells"), 2, 2).path("seatId").asLong()).isEqualTo(a1);
        assertThat(cell(after.path("cells"), 2, 1).path("seatId").asLong()).isEqualTo(a2);
        assertThat(after.path("fixtures")).hasSize(2);

        // Invalid: moving onto a staying seat, outside the grid, fixture offset outside the edge.
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor,
                "{\"features\":[],\"seatMoves\":[{\"seatId\":%d,\"row\":3,\"column\":1}]}".formatted(a1))
                .andExpect(status().isConflict());
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor,
                "{\"features\":[],\"seatMoves\":[{\"seatId\":%d,\"row\":9,\"column\":1}]}".formatted(a1))
                .andExpect(status().isUnprocessableEntity());
        send(put("/api/studyhalls/{id}/blocks/{b}/layout", hallId, blockId), vendor,
                "{\"features\":[],\"fixtures\":[{\"type\":\"AC\",\"position\":\"EAST\",\"offset\":7}]}")
                .andExpect(status().isUnprocessableEntity());
        // Shrinking below a fixture's offset is refused.
        send(put("/api/studyhalls/{id}/blocks/{b}", hallId, blockId), vendor,
                "{\"name\":\"Block M\",\"totalRows\":3,\"totalColumns\":1}").andExpect(status().isUnprocessableEntity());
    }

    private JsonNode layoutCells(long hallId, String vendor) throws Exception {
        return json(send(get("/api/studyhalls/{id}/layout", hallId), vendor, null).andExpect(status().isOk()))
                .path("floors").get(0).path("blocks").get(0).path("layout").path("cells");
    }

    private static JsonNode cell(JsonNode cells, int row, int column) {
        for (JsonNode cell : cells) {
            if (cell.path("row").asInt() == row && cell.path("column").asInt() == column) {
                return cell;
            }
        }
        throw new AssertionError("No cell at (" + row + "," + column + ")");
    }
}
