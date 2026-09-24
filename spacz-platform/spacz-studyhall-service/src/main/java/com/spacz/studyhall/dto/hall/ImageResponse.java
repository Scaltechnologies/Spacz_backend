package com.spacz.studyhall.dto.hall;

public record ImageResponse(Long id, String url, String caption, int displayOrder, boolean cover) {
}
