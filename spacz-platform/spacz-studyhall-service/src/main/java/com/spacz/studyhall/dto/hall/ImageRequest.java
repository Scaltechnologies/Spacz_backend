package com.spacz.studyhall.dto.hall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

@Schema(description = "An image already uploaded to object storage / CDN by the frontend")
public record ImageRequest(
        @Schema(example = "https://cdn.spacz.in/halls/12/front.jpg") @NotBlank @URL @Size(max = 500)
        @Pattern(regexp = "(?i)^\\s*https://\\S+\\s*$", message = "must be an https:// link") String url,
        @Size(max = 200) String caption,
        @Schema(description = "Makes this the cover image (the previous cover is unset)") boolean cover,
        @Min(0) Integer displayOrder) {
}
