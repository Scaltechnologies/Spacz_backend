package com.spacz.studyhall.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Helpers for MockMvc tests: authenticated and internal requests, JSON extraction, vendor setup.
 */
public abstract class ApiTestSupport {

    protected static final String KEY = "X-Internal-Api-Key";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;

    protected ResultActions send(MockHttpServletRequestBuilder request, String bearer, String body) throws Exception {
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    protected ResultActions internal(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(KEY, TestTokens.INTERNAL_KEY);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    protected JsonNode json(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isLessThan(300);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected long id(ResultActions actions, String field) throws Exception {
        return json(actions).path(field).asLong();
    }

    /** Registers (DRAFT), completes, submits and approves a vendor. */
    protected void approvedVendor(long vendorId) throws Exception {
        registeredVendor(vendorId);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/vendors/me"),
                TestTokens.bearer(vendorId, "VENDOR"), """
                        {"businessName":"Focus %d","contactName":"Owner","phone":"+919999999999",
                         "addressLine":"Plot 12","city":"Hyderabad"}""".formatted(vendorId))
                .andExpect(status().isOk());
        send(post("/api/vendors/me/submit"), TestTokens.bearer(vendorId, "VENDOR"), null).andExpect(status().isOk());
        internal(patch("/internal/vendors/{id}/status", vendorId), "{\"action\":\"APPROVE\"}").andExpect(status().isOk());
    }

    protected void registeredVendor(long vendorId) throws Exception {
        internal(post("/internal/vendors"), "{\"vendorId\":" + vendorId + ",\"phone\":\"+919999999999\"}")
                .andExpect(status().isCreated());
    }
}
