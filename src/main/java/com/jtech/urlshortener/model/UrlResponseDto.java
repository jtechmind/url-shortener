package com.jtech.urlshortener.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UrlResponseDto {
    private String shortUrl;
    private String shortCode;
    private String originalUrl;
    private LocalDateTime expiresAt;
    private Long clickCount;
}