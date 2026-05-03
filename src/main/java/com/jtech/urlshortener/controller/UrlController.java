package com.jtech.urlshortener.controller;

import com.jtech.urlshortener.model.UrlRequestDto;
import com.jtech.urlshortener.model.UrlResponseDto;
import com.jtech.urlshortener.service.ClickAnalyticsService;
import com.jtech.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Slf4j
public class UrlController {

    private final UrlService urlService;
    private final ClickAnalyticsService analyticsService;


    @PostMapping("/shorten")
    @Operation(summary = "Create short URL")
    public ResponseEntity<UrlResponseDto> shortenUrl(@Valid @RequestBody UrlRequestDto request) {
        String shortCode = urlService.shortenUrl(request);
        String shortUrl = "http://localhost:8080/api/v1/" + shortCode;

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UrlResponseDto.builder()
                        .shortUrl(shortUrl)
                        .shortCode(shortCode)
                        .originalUrl(request.getLongUrl())
                        .expiresAt(request.getExpiresAt())
                        .build());
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Redirect to original URL")
    public ResponseEntity<Void> redirectToOriginal(@PathVariable String shortCode) {
        String originalUrl = urlService.getOriginalUrl(shortCode);

        // Use 302 (temporary) for analytics tracking
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build();
    }

    @DeleteMapping("/{shortCode}")
    @Operation(summary = "Delete/disable short URL")
    public ResponseEntity<Void> deleteUrl(@PathVariable String shortCode) {
        urlService.deleteUrl(shortCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("URL Shortener is running!");
    }
}
