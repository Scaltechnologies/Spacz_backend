package com.spacz.user.client.dto;

public record ProgramDto(Long id, String code, String name, String description, String category, boolean active) {
}
