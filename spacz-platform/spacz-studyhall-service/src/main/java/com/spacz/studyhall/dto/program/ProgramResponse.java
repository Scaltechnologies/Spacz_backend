package com.spacz.studyhall.dto.program;

public record ProgramResponse(Long id, String code, String name, String description, String category,
                              boolean active) {
}
