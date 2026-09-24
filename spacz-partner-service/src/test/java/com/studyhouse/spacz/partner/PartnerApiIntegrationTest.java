package com.studyhouse.spacz.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyhouse.spacz.partner.repository.AmenityRepository;
import com.studyhouse.spacz.partner.repository.BlockRepository;
import com.studyhouse.spacz.partner.repository.ImageRepository;
import com.studyhouse.spacz.partner.repository.OwnerRepository;
import com.studyhouse.spacz.partner.repository.PropertyRepository;
import com.studyhouse.spacz.partner.repository.SeatRepository;

/**
 * Exercises the service through the same HTTP contract as the legacy `spacz` backend:
 * same paths, same request bodies, same status codes, same response fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PartnerApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private SeatRepository seatRepository;
    @Autowired
    private AmenityRepository amenityRepository;
    @Autowired
    private ImageRepository imageRepository;

    @BeforeEach
    void cleanDatabase() {
        amenityRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        imageRepository.deleteAllInBatch();
        blockRepository.deleteAllInBatch();
        propertyRepository.deleteAllInBatch();
        ownerRepository.deleteAllInBatch();
    }

    // ---------------------------------------------------------------- Partner flow (legacy docs, steps 3-9)

    @Test
    void legacyPartnerFlowProducesLegacyResponses() throws Exception {
        // STEP 3: POST /api/owners -> 200
        long ownerId = id(send(post("/api/owners"), "{\"ownerName\":\"Ravi\",\"ownerEmail\":\"ravi@example.com\","
                + "\"ownerPhoneNumber\":\"9876543210\",\"address\":\"Hyderabad\"}", status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ravi"))
                .andExpect(jsonPath("$.userLogin").isEmpty())
                .andExpect(jsonPath("$.properties", hasSize(0))), "ownerId");

        // STEP 4: POST /api/properties with owner: {ownerId} -> 200
        long propertyId = id(send(post("/api/properties"), "{\"propertyName\":\"Green Valley\",\"address\":"
                + "\"Ameerpet\",\"googleCoordinates\":\"17.43,78.44\",\"owner\":{\"ownerId\":" + ownerId + "}}",
                status().isOk())
                .andExpect(jsonPath("$.owner.ownerId").value(ownerId))
                .andExpect(jsonPath("$.owner.ownerName").value("Ravi"))
                .andExpect(jsonPath("$.images", hasSize(0)))
                .andExpect(jsonPath("$.blocks", hasSize(0))), "propertyId");

        // STEP 5: POST /images with property: {propertyId} -> 201
        long imageId = id(send(post("/images"), "{\"imageUrl\":\"https://cdn/1.jpg\",\"property\":{\"propertyId\":"
                + propertyId + "}}", status().isCreated())
                .andExpect(jsonPath("$.property.propertyId").value(propertyId))
                .andExpect(jsonPath("$.property.owner.ownerId").value(ownerId)), "imageId");

        // STEP 6: POST /api/blocks with property: {propertyId} -> 200
        long blockId = id(send(post("/api/blocks"), "{\"blockName\":\"AC Hall\",\"property\":{\"propertyId\":"
                + propertyId + "},\"blockDailyPrice\":150,\"blockMonthlyPrice\":2500}", status().isOk())
                .andExpect(jsonPath("$.blockDailyPrice").value(150.0))
                .andExpect(jsonPath("$.property.propertyName").value("Green Valley"))
                .andExpect(jsonPath("$.property.owner.ownerId").value(ownerId))
                .andExpect(jsonPath("$.seats", hasSize(0))), "blockId");

        // STEP 7: POST /amenities with block: {blockId} -> 201
        long amenityId = id(send(post("/amenities"), "{\"ac\":true,\"wifi\":true,\"water\":true,\"lockers\":false,"
                + "\"newspapers\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated())
                .andExpect(jsonPath("$.newspapers").value(true))
                .andExpect(jsonPath("$.block.blockId").value(blockId))
                .andExpect(jsonPath("$.block.property.propertyId").value(propertyId)), "amenityId");

        // STEP 8: POST /api/seats with block: {blockId} -> 200 ("reserved", and "isReserved" from the old docs)
        long seatId = id(send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId
                + "},\"seatPrice\":200,\"reserved\":true}", status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.seatPrice").value(200.0))
                .andExpect(jsonPath("$.block.blockName").value("AC Hall"))
                .andExpect(jsonPath("$.block.property.owner.ownerName").value("Ravi")), "seatId");
        send(post("/api/seats"), "{\"seatNumber\":\"A2\",\"block\":{\"blockId\":" + blockId
                + "},\"isReserved\":true}", status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));

        // Reads return the same data the legacy entities carried, down the whole tree
        perform(get("/api/owners/" + ownerId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.properties[0].propertyId").value(propertyId))
                .andExpect(jsonPath("$.properties[0].images[0].imageId").value(imageId))
                .andExpect(jsonPath("$.properties[0].blocks[0].blockId").value(blockId))
                .andExpect(jsonPath("$.properties[0].blocks[0].seats", hasSize(2)))
                .andExpect(jsonPath("$.properties[0].blocks[0].seats[0].seatNumber").value("A1"));
        perform(get("/api/properties/" + propertyId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.ownerEmail").value("ravi@example.com"))
                .andExpect(jsonPath("$.images[0].imageUrl").value("https://cdn/1.jpg"))
                .andExpect(jsonPath("$.blocks[0].blockName").value("AC Hall"))
                .andExpect(jsonPath("$.blocks[0].seats", hasSize(2)));
        perform(get("/api/blocks/" + blockId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.seats", hasSize(2)))
                .andExpect(jsonPath("$.property.owner.ownerId").value(ownerId));
        perform(get("/api/seats/" + seatId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.block.blockId").value(blockId));
        perform(get("/amenities/" + amenityId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ac").value(true));
        perform(get("/images/" + imageId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.property.propertyName").value("Green Valley"));

        for (String list : new String[] { "/api/owners", "/api/properties", "/api/blocks", "/api/seats",
                "/amenities", "/images" }) {
            perform(get(list)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(list.equals("/api/seats")
                    ? 2 : 1)));
        }
    }

    // ---------------------------------------------------------------- PUT semantics

    @Test
    void ownerPutReplacesFieldsLikeLegacy() throws Exception {
        long ownerId = createOwner("Ravi");
        send(put("/api/owners/" + ownerId), "{\"ownerName\":\"Ravi Kumar\",\"ownerPhoneNumber\":\"9000000000\"}",
                status().isOk())
                .andExpect(jsonPath("$.ownerName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.ownerPhoneNumber").value("9000000000"))
                .andExpect(jsonPath("$.ownerEmail").isEmpty())
                .andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    void propertyPutUpdatesAddressAndNowAlsoNameAndOwner() throws Exception {
        long ownerId = createOwner("Ravi");
        long otherOwnerId = createOwner("Sita");
        long propertyId = createProperty(ownerId, "Green Valley");

        // legacy behaviour: address is replaced
        send(put("/api/properties/" + propertyId), "{\"address\":\"SR Nagar\"}", status().isOk())
                .andExpect(jsonPath("$.address").value("SR Nagar"))
                .andExpect(jsonPath("$.propertyName").value("Green Valley"))
                .andExpect(jsonPath("$.owner.ownerId").value(ownerId));

        // name/owner were ignored by the legacy service; now saved when sent
        send(put("/api/properties/" + propertyId), "{\"propertyName\":\"Green Valley 2\",\"address\":\"SR Nagar\","
                + "\"owner\":{\"ownerId\":" + otherOwnerId + "}}", status().isOk())
                .andExpect(jsonPath("$.propertyName").value("Green Valley 2"))
                .andExpect(jsonPath("$.owner.ownerId").value(otherOwnerId));
    }

    @Test
    void blockPutUpdatesPropertyAndNowAlsoNameAndPrices() throws Exception {
        long ownerId = createOwner("Ravi");
        long propertyId = createProperty(ownerId, "Green Valley");
        long otherPropertyId = createProperty(ownerId, "Blue Hill");
        long blockId = createBlock(propertyId, "AC Hall");

        send(put("/api/blocks/" + blockId), "{\"property\":{\"propertyId\":" + otherPropertyId + "}}", status().isOk())
                .andExpect(jsonPath("$.property.propertyId").value(otherPropertyId))
                .andExpect(jsonPath("$.blockName").value("AC Hall"));

        send(put("/api/blocks/" + blockId), "{\"blockName\":\"Silent Room\",\"blockDailyPrice\":90,"
                + "\"blockMonthlyPrice\":1800}", status().isOk())
                .andExpect(jsonPath("$.blockName").value("Silent Room"))
                .andExpect(jsonPath("$.blockDailyPrice").value(90.0))
                .andExpect(jsonPath("$.blockMonthlyPrice").value(1800.0))
                .andExpect(jsonPath("$.property.propertyId").value(otherPropertyId));
    }

    @Test
    void seatPutReplacesNumberAndReservedLikeLegacy() throws Exception {
        long blockId = createBlock(createProperty(createOwner("Ravi"), "Green Valley"), "AC Hall");
        long otherBlockId = createBlock(propertyRepository.findAll().get(0).getPropertyId(), "Silent Room");
        long seatId = id(send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId
                + "},\"seatPrice\":200,\"reserved\":true}", status().isOk()), "seatId");

        send(put("/api/seats/" + seatId), "{\"seatNumber\":\"B7\",\"block\":{\"blockId\":" + otherBlockId + "}}",
                status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("B7"))
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.seatPrice").value(200.0))
                .andExpect(jsonPath("$.block.blockId").value(otherBlockId));

        send(put("/api/seats/" + seatId), "{\"seatNumber\":\"B7\",\"reserved\":true,\"seatPrice\":250}",
                status().isOk())
                .andExpect(jsonPath("$.reserved").value(true))
                .andExpect(jsonPath("$.seatPrice").value(250.0))
                .andExpect(jsonPath("$.block.blockId").value(otherBlockId));
    }

    @Test
    void amenityAndImagePutReplaceFieldsLikeLegacy() throws Exception {
        long propertyId = createProperty(createOwner("Ravi"), "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        long amenityId = id(send(post("/amenities"), "{\"ac\":true,\"wifi\":true,\"block\":{\"blockId\":" + blockId
                + "}}", status().isCreated()), "amenityId");
        long imageId = id(send(post("/images"), "{\"imageUrl\":\"u1\",\"property\":{\"propertyId\":" + propertyId
                + "}}", status().isCreated()), "imageId");

        send(put("/amenities/" + amenityId), "{\"lockers\":true}", status().isOk())
                .andExpect(jsonPath("$.lockers").value(true))
                .andExpect(jsonPath("$.ac").value(false))
                .andExpect(jsonPath("$.wifi").value(false))
                .andExpect(jsonPath("$.block.blockId").value(blockId));

        send(put("/images/" + imageId), "{\"imageUrl\":\"u2\"}", status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("u2"))
                .andExpect(jsonPath("$.property.propertyId").value(propertyId));
    }

    // ---------------------------------------------------------------- Deletes

    @Test
    void deleteStatusCodesMatchLegacy() throws Exception {
        long propertyId = createProperty(createOwner("Ravi"), "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        long seatId = id(send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}",
                status().isOk()), "seatId");
        long amenityId = id(send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}",
                status().isCreated()), "amenityId");
        long imageId = id(send(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId
                + "}}", status().isCreated()), "imageId");

        perform(delete("/api/seats/" + seatId)).andExpect(status().isNoContent());
        perform(delete("/amenities/" + amenityId)).andExpect(status().isNoContent());
        perform(delete("/images/" + imageId)).andExpect(status().isNoContent());

        // legacy: unknown IDs -> 204 for owners/properties/blocks/seats, 404 for amenities/images
        for (String path : new String[] { "/api/owners", "/api/properties", "/api/blocks", "/api/seats" }) {
            perform(delete(path + "/424242")).andExpect(status().isNoContent());
        }
        expectNotFound(delete("/amenities/424242"), "Amenity not found with ID: 424242");
        expectNotFound(delete("/images/424242"), "Image not found with ID: 424242");
    }

    @Test
    void deletingOwnerCascadesToWholeListingIncludingAmenities() throws Exception {
        long ownerId = createOwner("Ravi");
        long propertyId = createProperty(ownerId, "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}", status().isOk());
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated());
        send(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}",
                status().isCreated());

        long otherBlockId = createBlock(createProperty(createOwner("Sita"), "Blue Hill"), "Hall");
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + otherBlockId + "}}", status().isCreated());

        perform(delete("/api/owners/" + ownerId)).andExpect(status().isNoContent());

        assertThat(ownerRepository.count()).isEqualTo(1);
        assertThat(propertyRepository.count()).isEqualTo(1);
        assertThat(blockRepository.count()).isEqualTo(1);
        assertThat(seatRepository.count()).isZero();
        assertThat(imageRepository.count()).isZero();
        assertThat(amenityRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingPropertyOrBlockCascadesIncludingAmenities() throws Exception {
        long propertyId = createProperty(createOwner("Ravi"), "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        long otherBlockId = createBlock(propertyId, "Silent Room");
        send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}", status().isOk());
        send(post("/api/seats"), "{\"seatNumber\":\"B1\",\"block\":{\"blockId\":" + otherBlockId + "}}",
                status().isOk());
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated());
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + otherBlockId + "}}", status().isCreated());
        send(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}",
                status().isCreated());

        perform(delete("/api/blocks/" + blockId)).andExpect(status().isNoContent());
        assertThat(blockRepository.count()).isEqualTo(1);
        assertThat(seatRepository.count()).isEqualTo(1);
        assertThat(amenityRepository.count()).isEqualTo(1);

        perform(delete("/api/properties/" + propertyId)).andExpect(status().isNoContent());
        assertThat(ownerRepository.count()).isEqualTo(1);
        assertThat(propertyRepository.count()).isZero();
        assertThat(blockRepository.count()).isZero();
        assertThat(seatRepository.count()).isZero();
        assertThat(amenityRepository.count()).isZero();
        assertThat(imageRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- Relationship endpoints (additive)

    @Test
    void relationshipEndpoints() throws Exception {
        long ownerId = createOwner("Ravi");
        long propertyId = createProperty(ownerId, "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}", status().isOk());
        send(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}",
                status().isCreated());

        expectNotFound(get("/api/blocks/" + blockId + "/amenity"), "Amenity not found for block with ID: " + blockId);
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated());

        perform(get("/api/owners/" + ownerId + "/properties")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/properties/" + propertyId + "/blocks")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/properties/" + propertyId + "/images")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/blocks/" + blockId + "/seats")).andExpect(jsonPath("$", hasSize(1)));
        perform(get("/api/blocks/" + blockId + "/amenity")).andExpect(jsonPath("$.ac").value(true));
    }

    // ---------------------------------------------------------------- Error cases

    @Test
    void createWithMissingOrNonExistingParentIsRejected() throws Exception {
        Map<String, String[]> cases = Map.of(
                "/api/properties", new String[] { "{\"propertyName\":\"X\"}",
                        "{\"propertyName\":\"X\",\"owner\":{\"ownerId\":999}}", "Owner", "owner.ownerId" },
                "/api/blocks", new String[] { "{\"blockName\":\"X\"}",
                        "{\"blockName\":\"X\",\"property\":{\"propertyId\":999}}", "Property", "property.propertyId" },
                "/api/seats", new String[] { "{\"seatNumber\":\"X\"}",
                        "{\"seatNumber\":\"X\",\"block\":{\"blockId\":999}}", "Block", "block.blockId" },
                "/amenities", new String[] { "{\"ac\":true}",
                        "{\"ac\":true,\"block\":{\"blockId\":999}}", "Block", "block.blockId" },
                "/images", new String[] { "{\"imageUrl\":\"u\"}",
                        "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":999}}", "Property", "property.propertyId" });
        for (Map.Entry<String, String[]> c : cases.entrySet()) {
            send(post(c.getKey()), c.getValue()[0], status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(c.getValue()[3] + " is required"));
            expectNotFound(post(c.getKey()).contentType(MediaType.APPLICATION_JSON).content(c.getValue()[1]),
                    c.getValue()[2] + " not found with ID: 999");
        }
        assertThat(propertyRepository.count() + blockRepository.count() + seatRepository.count()
                + amenityRepository.count() + imageRepository.count()).isZero();
    }

    @Test
    void unknownIdsReturn404OnGetAndPut() throws Exception {
        String[][] resources = { { "/api/owners", "Owner" }, { "/api/properties", "Property" },
                { "/api/blocks", "Block" }, { "/api/seats", "Seat" }, { "/amenities", "Amenity" },
                { "/images", "Image" } };
        for (String[] r : resources) {
            String message = r[1] + " not found with ID: 424242";
            expectNotFound(get(r[0] + "/424242"), message);
            expectNotFound(put(r[0] + "/424242").contentType(MediaType.APPLICATION_JSON).content("{}"), message);
        }
        expectNotFound(get("/api/properties/424242/blocks"), "Property not found with ID: 424242");
    }

    @Test
    void secondAmenityForSameBlockIsConflict() throws Exception {
        long blockId = createBlock(createProperty(createOwner("Ravi"), "Green Valley"), "AC Hall");
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated());
        send(post("/amenities"), "{\"wifi\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void malformedRequestsReturnCleanErrorBodies() throws Exception {
        perform(post("/api/owners").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        perform(get("/api/owners/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'abc' for parameter 'id'"));
        perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void ownerWithoutNameIsStillAcceptedLikeLegacy() throws Exception {
        send(post("/api/owners"), "{}", status().isOk()).andExpect(jsonPath("$.ownerName").isEmpty());
    }

    // ---------------------------------------------------------------- Serialization

    @Test
    void responsesContainNoBackReferencesAndStayShallow() throws Exception {
        long ownerId = createOwner("Ravi");
        long propertyId = createProperty(ownerId, "Green Valley");
        long blockId = createBlock(propertyId, "AC Hall");
        send(post("/api/seats"), "{\"seatNumber\":\"A1\",\"block\":{\"blockId\":" + blockId + "}}", status().isOk());
        send(post("/amenities"), "{\"ac\":true,\"block\":{\"blockId\":" + blockId + "}}", status().isCreated());
        send(post("/images"), "{\"imageUrl\":\"u\",\"property\":{\"propertyId\":" + propertyId + "}}",
                status().isCreated());

        String[] endpoints = { "/api/owners", "/api/owners/" + ownerId, "/api/properties",
                "/api/properties/" + propertyId, "/api/blocks", "/api/blocks/" + blockId, "/api/seats", "/amenities",
                "/images", "/api/owners/" + ownerId + "/properties", "/api/blocks/" + blockId + "/amenity" };
        for (String endpoint : endpoints) {
            JsonNode json = objectMapper.readTree(perform(get(endpoint)).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString());
            // deepest legal path: [ owner -> properties -> [ property -> blocks -> [ block -> seats -> [ seat
            assertThat(depth(json)).as(endpoint).isLessThanOrEqualTo(8);
        }

        JsonNode owner = objectMapper.readTree(perform(get("/api/owners/" + ownerId)).andReturn().getResponse()
                .getContentAsString());
        JsonNode property = owner.get("properties").get(0);
        assertThat(property.has("owner")).isFalse();
        assertThat(property.get("blocks").get(0).has("property")).isFalse();
        assertThat(property.get("blocks").get(0).get("seats").get(0).has("block")).isFalse();
        assertThat(property.get("images").get(0).has("property")).isFalse();

        JsonNode seat = objectMapper.readTree(perform(get("/api/seats")).andReturn().getResponse()
                .getContentAsString()).get(0);
        assertThat(seat.get("block").has("seats")).isFalse();
        assertThat(seat.get("block").get("property").has("blocks")).isFalse();
        assertThat(seat.get("block").get("property").get("owner").has("properties")).isFalse();
    }

    @Test
    void ownerLoginLinkRoundTrips() throws Exception {
        send(post("/api/owners"), "{\"ownerName\":\"Ravi\",\"userLogin\":{\"loginId\":7}}", status().isOk())
                .andExpect(jsonPath("$.userLogin.loginId").value(7));
    }

    // ---------------------------------------------------------------- Swagger

    @Test
    void openApiDocumentListsAllPartnerApis() throws Exception {
        String body = perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(body).contains("Owner APIs", "Property APIs", "Block APIs", "Seat APIs", "Amenity APIs",
                "Image APIs", "\"/api/owners\"", "\"/api/properties\"", "\"/api/blocks\"", "\"/api/seats\"",
                "\"/amenities\"", "\"/images\"", "OwnerRequest", "ErrorResponse", "\"OwnerResponse\"",
                "\"PropertyResponse\"", "\"BlockResponse\"", "\"SeatResponse\"", "\"AmenityResponse\"",
                "\"ImageResponse\"");
        assertThat(body).doesNotContain("\"/api/amenities\"", "\"/api/images\"");
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions perform(RequestBuilder request) throws Exception {
        return mvc.perform(request);
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String json, ResultMatcher expectedStatus) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(expectedStatus);
    }

    private long id(ResultActions result, String idField) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get(idField).asLong();
    }

    private long createOwner(String name) throws Exception {
        return id(send(post("/api/owners"), "{\"ownerName\":\"" + name + "\",\"ownerEmail\":\"" + name.toLowerCase()
                + "@example.com\",\"address\":\"Hyderabad\"}", status().isOk()), "ownerId");
    }

    private long createProperty(long ownerId, String name) throws Exception {
        return id(send(post("/api/properties"), "{\"propertyName\":\"" + name + "\",\"owner\":{\"ownerId\":"
                + ownerId + "}}", status().isOk()), "propertyId");
    }

    private long createBlock(long propertyId, String name) throws Exception {
        return id(send(post("/api/blocks"), "{\"blockName\":\"" + name + "\",\"property\":{\"propertyId\":"
                + propertyId + "}}", status().isOk()), "blockId");
    }

    private void expectNotFound(RequestBuilder request, String message) throws Exception {
        mvc.perform(request).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").exists());
    }

    private static int depth(JsonNode node) {
        if (!node.isContainerNode()) {
            return 0;
        }
        int max = 0;
        for (Iterator<JsonNode> it = node.elements(); it.hasNext();) {
            max = Math.max(max, depth(it.next()));
        }
        return max + 1;
    }
}
