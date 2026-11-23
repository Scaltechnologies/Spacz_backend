package com.spacz.admin.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/settings")
@RequiredArgsConstructor
@Tag(name = "Internal - Settings", description = "Platform settings for other services")
@SecurityRequirement(name = "internalApiKey")
public class InternalSettingsController {

    private final PlatformSettingRepository repository;

    @GetMapping("/{key}")
    @Transactional(readOnly = true)
    @Operation(summary = "{key, value} of one setting, or {} when it is not set")
    public Map<String, String> get(@PathVariable String key) {
        return repository.findBySettingKey(key)
                .map(s -> Map.of("key", s.getSettingKey(), "value", s.getSettingValue()))
                .orElse(Map.of());
    }
}
