package com.spacz.user.controller;

import com.spacz.user.config.OpenApiConfig;
import com.spacz.user.dto.UserDocumentResponse;
import com.spacz.user.entity.UserDocument;
import com.spacz.user.exception.ApiError;
import com.spacz.user.security.AuthenticatedUser;
import com.spacz.user.service.UserDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * The caller's own identity documents. Files are only ever returned to their owner.
 */
@RestController
@RequestMapping("/api/users/me/documents")
@RequiredArgsConstructor
@Tag(name = "User documents", description = "Aadhaar front/back uploads (JPEG/PNG, private)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ApiError.class)))
public class UserDocumentController {

    private final UserDocumentService documentService;

    @GetMapping
    @Operation(summary = "My uploaded documents (types not listed are not uploaded yet)")
    public List<UserDocumentResponse> list(@AuthenticationPrincipal AuthenticatedUser caller) {
        return documentService.list(caller.userId());
    }

    @PutMapping(path = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload or replace a document",
            description = "multipart/form-data with part `file`. JPEG or PNG (checked from the file content), "
                    + "at most spacz.storage.max-file-bytes (1.5 MB). A re-upload resets verification to PENDING.")
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "EMPTY_FILE / UNSUPPORTED_FILE_TYPE", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public UserDocumentResponse upload(@AuthenticationPrincipal AuthenticatedUser caller,
                                       @PathVariable UserDocument.Type type,
                                       @RequestPart("file") MultipartFile file) {
        return documentService.upload(caller.userId(), type, file);
    }

    @GetMapping("/{type}/file")
    @Operation(summary = "Download my document image", description = "Not cacheable; never publicly accessible.")
    @ApiResponse(responseCode = "404", description = "Not uploaded", content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<InputStreamResource> file(@AuthenticationPrincipal AuthenticatedUser caller,
                                                    @PathVariable UserDocument.Type type) {
        UserDocumentService.StoredFile stored = documentService.open(caller.userId(), type);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .contentLength(stored.sizeBytes())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(stored.content()));
    }
}
