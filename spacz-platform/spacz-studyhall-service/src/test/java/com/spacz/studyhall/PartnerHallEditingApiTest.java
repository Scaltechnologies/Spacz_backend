package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Partner app hall editing: alternate phone, photo cover/order, HTTPS-only links and the atomic seating batch. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartnerHallEditingApiTest extends ApiTestSupport {

    @MockitoBean
    private UserServiceClient userServiceClient;

    private long hall(String vendor, String name) throws Exception {
        return id(send(post("/api/studyhalls"), vendor, """
                {"name":"%s","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "latitude":17.4375,"longitude":78.4482,"contactPhone":"+919812345678","alternatePhone":"+919812345679",
                 "openingTime":"06:00","closingTime":"22:00"}""".formatted(name)), "id");
    }

    @Test
    void alternatePhoneIsSavedAndCleared() throws Exception {
        long vendorId = 701;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = hall(vendor, "Alt Phone Hall");

        send(get("/api/studyhalls/{id}", hallId), vendor, null)
                .andExpect(jsonPath("$.alternatePhone").value("+919812345679"))
                .andExpect(jsonPath("$.latitude").value(17.4375));

        send(put("/api/studyhalls/{id}", hallId), vendor, """
                {"name":"Alt Phone Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "latitude":17.44,"longitude":78.45,"alternatePhone":"+919800000000"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alternatePhone").value("+919800000000"))
                .andExpect(jsonPath("$.longitude").value(78.45));

        send(put("/api/studyhalls/{id}", hallId), vendor, """
                {"name":"Alt Phone Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "alternatePhone":"not a phone"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void photosAreHttpsOnlyAndCanBeReorderedAndReCovered() throws Exception {
        long vendorId = 702;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = hall(vendor, "Photo Order Hall");

        send(post("/api/studyhalls/{id}/images", hallId), vendor, "{\"url\":\"http://cdn.example.com/a.jpg\"}")
                .andExpect(status().isBadRequest());

        List<Long> ids = new ArrayList<>();
        for (String name : List.of("a", "b", "c")) {
            JsonNode detail = json(send(post("/api/studyhalls/{id}/images", hallId), vendor,
                    "{\"url\":\"https://cdn.example.com/" + name + ".jpg\"}"));
            ids.add(detail.path("images").get(detail.path("images").size() - 1).path("id").asLong());
        }

        // c, a, b
        send(put("/api/studyhalls/{id}/images/order", hallId), vendor,
                "{\"imageIds\":[" + ids.get(2) + "," + ids.get(0) + "," + ids.get(1) + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.images[1].id").value(ids.get(0)))
                .andExpect(jsonPath("$.images[2].id").value(ids.get(1)));

        // a partial list is refused
        send(put("/api/studyhalls/{id}/images/order", hallId), vendor, "{\"imageIds\":[" + ids.get(0) + "]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_IMAGE_ORDER"));

        // exactly one cover, the chosen one; caption saved
        JsonNode detail = json(send(patch("/api/studyhalls/{id}/images/{img}", hallId, ids.get(1)), vendor,
                "{\"cover\":true,\"caption\":\"Reading room\"}"));
        long covers = 0;
        for (JsonNode image : detail.path("images")) {
            if (image.path("cover").asBoolean()) {
                covers++;
                assertThat(image.path("id").asLong()).isEqualTo(ids.get(1));
                assertThat(image.path("caption").asText()).isEqualTo("Reading room");
            }
        }
        assertThat(covers).isEqualTo(1);

        // another vendor cannot touch these photos
        long otherId = 703;
        approvedVendor(otherId);
        send(patch("/api/studyhalls/{id}/images/{img}", hallId, ids.get(0)), TestTokens.bearer(otherId, "VENDOR"),
                "{\"cover\":true}")
                .andExpect(status().is4xxClientError());
    }

    @Test
    void seatingBatchAppliesEverythingOrNothing() throws Exception {
        long vendorId = 704;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = hall(vendor, "Batch Hall");

        // a new floor and a block on it (by ref), then that block's fixtures (by ref)
        JsonNode result = json(send(post("/api/studyhalls/{id}/layout/batch", hallId), vendor, """
                {"operations":[
                  {"type":"CREATE_FLOOR","ref":"f1","floor":{"floorNumber":1,"name":"First Floor"}},
                  {"type":"CREATE_BLOCK","ref":"b1","floorRef":"f1",
                   "createBlock":{"name":"Hall A","layoutTemplate":"s s s\\ns . s"}},
                  {"type":"UPDATE_LAYOUT","blockRef":"b1","layout":{"features":[]}}
                ]}"""));
        assertThat(result.path("applied").asInt()).isEqualTo(3);
        long floorId = result.path("floorIds").path("f1").asLong();
        long blockId = result.path("blockIds").path("b1").asLong();
        assertThat(floorId).isPositive();
        assertThat(blockId).isPositive();

        JsonNode blocks = json(send(get("/api/studyhalls/{id}/blocks", hallId), vendor, null));
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).path("seats")).hasSize(5);

        // second batch: renames the block, then a step fails -> the rename and the new floor must not persist
        send(post("/api/studyhalls/{id}/layout/batch", hallId), vendor, """
                {"operations":[
                  {"type":"CREATE_FLOOR","ref":"f2","floor":{"floorNumber":2,"name":"Second Floor"}},
                  {"type":"UPDATE_BLOCK","blockId":%d,"updateBlock":{"name":"Renamed","totalRows":2,"totalColumns":3}},
                  {"type":"DELETE_SEAT","seatId":999999}
                ]}""".formatted(blockId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Layout not saved, nothing was changed. Step 3")));

        JsonNode after = json(send(get("/api/studyhalls/{id}/blocks", hallId), vendor, null));
        assertThat(after.get(0).path("name").asText()).isEqualTo("Hall A");
        JsonNode floors = json(send(get("/api/studyhalls/{id}/floors", hallId), vendor, null));
        for (JsonNode f : floors) {
            assertThat(f.path("name").asText()).isNotEqualTo("Second Floor");
        }

        // unknown ref, and another vendor's hall
        send(post("/api/studyhalls/{id}/layout/batch", hallId), vendor, """
                {"operations":[{"type":"UPDATE_LAYOUT","blockRef":"nope","layout":{"features":[]}}]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_BATCH"));
        long otherId = 705;
        approvedVendor(otherId);
        send(post("/api/studyhalls/{id}/layout/batch", hallId), TestTokens.bearer(otherId, "VENDOR"), """
                {"operations":[{"type":"DELETE_BLOCK","blockId":%d}]}""".formatted(blockId))
                .andExpect(status().is4xxClientError());
        assertThat(json(send(get("/api/studyhalls/{id}/blocks", hallId), vendor, null))).hasSize(1);
    }
}
