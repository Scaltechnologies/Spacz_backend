package com.spacz.admin.client.dto;

/**
 * @param action APPROVE, REJECT, SUSPEND or ACTIVATE
 */
public record StatusActionDto(String action, String reason) {
}
