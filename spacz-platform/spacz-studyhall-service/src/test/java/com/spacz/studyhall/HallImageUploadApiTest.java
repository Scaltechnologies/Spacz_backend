package com.spacz.studyhall;

import com.fasterxml.jackson.databind.JsonNode;
import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.support.ApiTestSupport;
import com.spacz.studyhall.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vendor photo upload → stored file → public media URL on the host the client called. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HallImageUploadApiTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @MockitoBean
    private UserServiceClient userServiceClient;

    @Test
    void uploadServeAndDeleteHallPhoto() throws Exception {
        long vendorId = 620;
        String vendor = TestTokens.bearer(vendorId, "VENDOR");
        approvedVendor(vendorId);
        long hallId = id(send(post("/api/studyhalls"), vendor, """
                {"name":"Photo Hall","addressLine":"Plot 1","city":"Hyderabad","state":"Telangana","pricePerDay":100.00,
                 "openingTime":"06:00","closingTime":"22:00"}"""), "id");

        // through the gateway the request carries the phone-reachable host
        JsonNode hall = json(mvc.perform(multipart("/api/studyhalls/{id}/images/upload", hallId)
                        .file(new MockMultipartFile("file", "front.png", "image/png", PNG))
                        .header("Authorization", vendor)
                        .header("X-Forwarded-Host", "192.168.0.107:8080")
                        .header("X-Forwarded-Proto", "http"))
                .andExpect(status().isCreated()));
        JsonNode image = hall.path("images").get(0);
        String url = image.path("url").asText();
        assertThat(url).startsWith("http://192.168.0.107:8080/api/studyhalls/media/" + hallId + "/").endsWith(".png");
        assertThat(image.path("cover").asBoolean()).isTrue();

        // public, served with its content type
        String path = url.substring(url.indexOf("/api/"));
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));

        // hall details (owner view of a draft) resolve the URL for whichever host the caller used
        JsonNode search = json(mvc.perform(get("/api/studyhalls/{id}", hallId).header("Authorization", vendor)
                .header("X-Forwarded-Host", "10.0.2.2:8080")));
        assertThat(search.path("images").get(0).path("url").asText()).startsWith("http://10.0.2.2:8080/api/studyhalls/media/");

        // not an image / path tricks are rejected
        mvc.perform(multipart("/api/studyhalls/{id}/images/upload", hallId)
                        .file(new MockMultipartFile("file", "x.png", "image/png", "not an image".getBytes()))
                        .header("Authorization", vendor))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/studyhalls/media/{h}/{f}", hallId, "..%2F..%2Fsecret.png")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/studyhalls/media/{h}/{f}", hallId, "secret.png")).andExpect(status().isNotFound());

        // deleting the image removes the file
        long imageId = image.path("id").asLong();
        send(delete("/api/studyhalls/{id}/images/{img}", hallId, imageId), vendor, null).andExpect(status().isOk());
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }
}
