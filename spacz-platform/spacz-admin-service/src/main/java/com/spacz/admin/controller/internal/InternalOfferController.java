package com.spacz.admin.controller.internal;

import com.spacz.admin.dto.OfferResponse;
import com.spacz.admin.service.OfferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/offers")
@RequiredArgsConstructor
@Tag(name = "Internal - Offers", description = "Live offers for booking pricing in studyhall-service")
@SecurityRequirement(name = "internalApiKey")
public class InternalOfferController {

    private final OfferService offerService;

    @GetMapping("/live")
    @Operation(summary = "Offers live right now, with their discount rules")
    public List<OfferResponse> live() {
        return offerService.live();
    }
}
