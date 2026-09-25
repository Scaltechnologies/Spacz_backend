package com.spacz.studyhall;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The spacz-partner-service integration tests, ported to the legacy-compatible API of studyhall-service.
 * Same paths, bodies, fields and status codes; every call now carries a VENDOR token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyPartnerApiTest extends ApiTestSupport {

    private static final AtomicLong VENDORS = new AtomicLong(7000);

    @MockitoBean
    private UserServiceClient userServiceClient;

    private String token;

    private ResultActions as(MockHttpServletRequestBuilder request, String body) throws Exception {
        return send(request, token, body);
    }

    private long newVendor() throws Exception {
        long vendorId = VENDORS.incrementAndGet();
        registeredVendor(vendorId);
        token = TestTokens.bearer(vendorId, "VENDOR");
        return vendorId;
    }

    private long owner() throws Exception {
        return id(as(post("/api/owners"), "{\"ownerName\":\"Ravi\",\"ownerEmail\":\"ravi@example.com\","
                + "\"ownerPhoneNumber\":\"9876543210\",\"address\":\"Hyderabad\"}").andExpect(status().isOk()), "ownerId");
    }

    private long property(long ownerId, String name) throws Exception {
        return id(as(post("/api/properties"), "{\"propertyName\":\"" + name + "\",\"address\":\"Ameerpet\","
                + "\"googleCoordinates\":\"17.4375,78.4483\",\"owner\":{\"ownerId\":" + ownerId + "}}")
                .andExpect(status().isOk()), "propertyId");
    }

    private long block(long propertyId, String name) throws Exception {
        return id(as(post("/api/blocks"), "{\"blockName\":\"" + name + "\",\"property\":{\"propertyId\":" + propertyId
                + "},\"blockDailyPrice\":150,\"blockMonthlyPrice\":2500}").andExpect(status().isOk()), "blockId");
    }

    @Test
    void legacyPartnerFlowProducesLegacyResponses() throws Exception {
        long vendorId = newVendor();
        long ownerId = id(as(post("/api/owners"), "{\"ownerName\":\"Ravi\",\"ownerEmail\":\"ravi@example.com\","
                + "\"ownerPhoneNumber\":\"9876543210\",\"address\":\"Hyderabad\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ravi"))
                .andExpect(jsonPath("$.userLogin.loginId").value(vendorId))
                .andExpect(jsonPath("$.properties", hasSize(0))), "ownerId");

        long propertyId = id(as(post("/api/properties"), "{\"propertyName\":\"Green Valley\",\"address\":\"Ameerpet\","
                + "\"googleCoordinates\":\"17.4375,78.4483\",\"owner\":{\"ownerId\":" + ownerId + "}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.ownerId").value(ownerId))
                .andExpect(jsonPath("$.googleCoordinates").value("17.4375,78.4483"))
                .andExpect(jsonPath("$.images", hasSize(0)))
                .andExpect(jsonPath("$.blocks", hasSize(0))), "propertyId");

        long imageId = id(as(post("/images"), "{\"imageUrl\":\"https://cdn/1.jpg\",\"property\":{\"propertyId\":"
                + propertyId + "}}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.property.propertyId").value(propertyId))
                .andExpect(jsonPath("$.property.owner.ownerId").value(ownerId)), "imageId");

        long blockId = id(as(post("/api/blocks"), "{\"blockName\":\"AC Hall\",\"property\":{\"propertyId\":"
                + propertyId + "},\"blockDailyPrice\":150,\"blockMonthlyPrice\":2500}").andExpect(status().isOk())
                .andExpect(jsonPath("$.blockDailyPrice").value(150.0))
                .andExpect(jsonPath("$.property.propertyName").value("Green Valley"))
                .andExpect(jsonPath("$.seats", hasSize(0))), "blockId");

        long amenityId = id(as(post("/amenities"), "{\"ac\":true,\"wifi\":true,\"water\":false,\"lockers\":false,"
                + "\"newspapers\":true,\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.newspapers").value(true))
                .andExpect(jsonPath("$.water").value(false))
                .andExpect(jsonPath("$.block.blockId").value(blockId))
                .andExpect(jsonPath("$.block.property.propertyId").value(propertyId)), "amenityId");

        long seatId = id(as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId
                + "},\"seatPrice\":200,\"reserved\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.seatPrice").value(200.0))
                .andExpect(jsonPath("$.block.blockName").value("AC Hall"))
                .andExpect(jsonPath("$.block.property.owner.ownerName").value("Ravi")), "seatId");
        as(post("/api/seats"), "{\"seatNumber\":\"A2\",\"block\":{\"blockId\":" + blockId + "},\"isReserved\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved").value(true));

        as(get("/api/owners/" + ownerId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.properties[0].propertyId").value(propertyId))
                .andExpect(jsonPath("$.properties[0].images[0].imageId").value(imageId))
                .andExpect(jsonPath("$.properties[0].blocks[0].blockId").value(blockId))
                .andExpect(jsonPath("$.properties[0].blocks[0].seats", hasSize(2)))
                .andExpect(jsonPath("$.properties[0].blocks[0].seats[0].seatNumber").value("A1"));
        as(get("/api/properties/" + propertyId), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.ownerEmail").value("ravi@example.com"))
                .andExpect(jsonPath("$.images[0].imageUrl").value("https://cdn/1.jpg"))
                .andExpect(jsonPath("$.blocks[0].seats", hasSize(2)));
        as(get("/api/blocks/" + blockId), null).andExpect(jsonPath("$.seats", hasSize(2)))
                .andExpect(jsonPath("$.property.owner.ownerId").value(ownerId));
        as(get("/api/seats/" + seatId), null).andExpect(jsonPath("$.block.blockId").value(blockId));
        as(get("/amenities/" + amenityId), null).andExpect(jsonPath("$.ac").value(true));
        as(get("/images/" + imageId), null).andExpect(jsonPath("$.property.propertyName").value("Green Valley"));
        for (String list : new String[]{"/api/owners", "/api/properties", "/api/blocks", "/amenities", "/images"}) {
            as(get(list), null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        }
        as(get("/api/seats"), null).andExpect(jsonPath("$", hasSize(2)));

        // The same data is visible through the new API: the property is a study hall awaiting approval.
        mvc.perform(get("/api/studyhalls/{id}", propertyId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.blocks[0].monthlyPrice").value(2500.00))
                .andExpect(jsonPath("$.blocks[0].amenities.length()").value(3));
        internal(get("/internal/vendors/{id}", vendorId), null).andExpect(jsonPath("$.profile.status").value("PENDING"));
    }

    @Test
    void putReplacesFieldsLikeLegacy() throws Exception {
        newVendor();
        long ownerId = owner();
        as(put("/api/owners/" + ownerId), "{\"ownerName\":\"Ravi Kumar\",\"ownerPhoneNumber\":\"9000000000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.ownerEmail").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty());

        long propertyId = property(ownerId, "Green Valley");
        as(put("/api/properties/" + propertyId), "{\"address\":\"SR Nagar\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("SR Nagar"))
                .andExpect(jsonPath("$.propertyName").value("Green Valley"));

        long blockId = block(propertyId, "AC Hall");
        long otherPropertyId = property(ownerId, "Second");
        as(put("/api/blocks/" + blockId), "{\"property\":{\"propertyId\":" + otherPropertyId + "}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.property.propertyId").value(otherPropertyId))
                .andExpect(jsonPath("$.blockName").value("AC Hall"));
        as(put("/api/blocks/" + blockId), "{\"blockName\":\"Silent Room\",\"blockDailyPrice\":90,\"blockMonthlyPrice\":1800}")
                .andExpect(jsonPath("$.blockName").value("Silent Room"))
                .andExpect(jsonPath("$.blockDailyPrice").value(90.0))
                .andExpect(jsonPath("$.blockMonthlyPrice").value(1800.0));

        long seatId = id(as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId
                + "},\"seatPrice\":200,\"reserved\":true}"), "seatId");
        long otherBlockId = block(propertyId, "Other");
        as(put("/api/seats/" + seatId), "{\"seatNumber\":\"B7\",\"block\":{\"blockId\":" + otherBlockId + "}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("B7"))
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.seatPrice").value(200.0))
                .andExpect(jsonPath("$.block.blockId").value(otherBlockId));

        long amenityId = id(as(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + otherBlockId + "}}")
                .andExpect(status().isCreated()), "amenityId");
        as(put("/amenities/" + amenityId), "{\"lockers\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lockers").value(true))
                .andExpect(jsonPath("$.ac").value(false))
                .andExpect(jsonPath("$.block.blockId").value(otherBlockId));

        long imageId = id(as(post("/images"), "{\"imageUrl\":\"u1\",\"property\":{\"propertyId\":" + propertyId + "}}")
                .andExpect(status().isCreated()), "imageId");
        as(put("/images/" + imageId), "{\"imageUrl\":\"u2\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("u2"))
                .andExpect(jsonPath("$.property.propertyId").value(propertyId));
    }

    @Test
    void deleteStatusCodesMatchLegacyAndCascade() throws Exception {
        newVendor();
        long ownerId = owner();
        long propertyId = property(ownerId, "Green Valley");
        long blockId = block(propertyId, "AC Hall");
        long seatId = id(as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}"), "seatId");
        long amenityId = id(as(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}"), "amenityId");
        long imageId = id(as(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}"), "imageId");

        as(delete("/api/seats/" + seatId), null).andExpect(status().isNoContent());
        as(delete("/amenities/" + amenityId), null).andExpect(status().isNoContent());
        as(delete("/images/" + imageId), null).andExpect(status().isNoContent());
        for (String path : new String[]{"/api/owners", "/api/properties", "/api/blocks", "/api/seats", "/amenities", "/images"}) {
            as(delete(path + "/424242"), null).andExpect(status().isNoContent());
        }
        as(get("/amenities/" + amenityId), null).andExpect(status().isNotFound());

        as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isOk());
        as(delete("/api/owners/" + ownerId), null).andExpect(status().isNoContent());
        as(get("/api/properties/" + propertyId), null).andExpect(status().isNotFound());
        as(get("/api/blocks/" + blockId), null).andExpect(status().isNotFound());
        as(get("/api/owners"), null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void relationshipEndpoints() throws Exception {
        newVendor();
        long ownerId = owner();
        long propertyId = property(ownerId, "P");
        long blockId = block(propertyId, "B");
        as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isOk());
        as(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}").andExpect(status().isCreated());
        as(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isCreated());

        as(get("/api/owners/" + ownerId + "/properties"), null).andExpect(jsonPath("$", hasSize(1)));
        as(get("/api/properties/" + propertyId + "/blocks"), null).andExpect(jsonPath("$", hasSize(1)));
        as(get("/api/properties/" + propertyId + "/images"), null).andExpect(jsonPath("$", hasSize(1)));
        as(get("/api/blocks/" + blockId + "/seats"), null).andExpect(jsonPath("$", hasSize(1)));
        as(get("/api/blocks/" + blockId + "/amenity"), null).andExpect(jsonPath("$.ac").value(true));
    }

    @Test
    void missingParentsUnknownIdsConflictsAndMalformedRequests() throws Exception {
        newVendor();
        long ownerId = owner();
        long blockId = block(property(ownerId, "P"), "B");
        as(post("/api/properties"), "{\"propertyName\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("owner.ownerId is required"));
        as(post("/api/blocks"), "{\"blockName\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("property.propertyId is required"));
        as(post("/api/seats"), "{\"seatNumber\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("block.blockId is required"));
        as(post("/api/blocks"), "{\"property\":{\"propertyId\":424242}}").andExpect(status().isNotFound());
        as(get("/api/seats/424242"), null).andExpect(status().isNotFound());
        as(put("/api/owners/424242"), "{}").andExpect(status().isNotFound());

        as(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isCreated());
        as(post("/amenities"), "{\"wifi\":true,\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        as(post("/api/owners"), "{\"ownerName\":\"Again\"}").andExpect(status().isConflict());

        mvc.perform(post("/api/owners").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        as(get("/api/owners/abc"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'id'"));
    }

    @Test
    void everyCallIsScopedToTheCallingVendor() throws Exception {
        newVendor();
        long ownerId = owner();
        long propertyId = property(ownerId, "Mine");
        String mine = token;

        newVendor();
        as(get("/api/properties/" + propertyId), null).andExpect(status().isNotFound());
        as(get("/api/properties"), null).andExpect(jsonPath("$", hasSize(0)));
        as(post("/api/properties"), "{\"propertyName\":\"x\",\"owner\":{\"ownerId\":" + ownerId + "}}")
                .andExpect(status().isNotFound());
        as(delete("/api/properties/" + propertyId), null).andExpect(status().isNoContent());

        mvc.perform(get("/api/properties/" + propertyId).header(HttpHeaders.AUTHORIZATION, mine)).andExpect(status().isOk());
        mvc.perform(get("/api/properties")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/properties").header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(5, "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void propertyCreatedInThePartnerAppGoesLiveAfterAdminApproval() throws Exception {
        long vendorId = newVendor();
        long ownerId = owner();
        long propertyId = property(ownerId, "Live Hall");
        long blockId = block(propertyId, "Main");
        as(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}").andExpect(status().isOk());

        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
        internal(patch("/internal/studyhalls/{id}/status", propertyId), "{\"action\":\"APPROVE\"}")
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/studyhalls/{id}/seats", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].seats[0].pricePerDay").value(150.00))
                .andExpect(jsonPath("$.blocks[0].seats[0].available").value(true));
    }
}
