package com.jtech.urlshortener.service;

import com.jtech.urlshortener.exception.DuplicateCodeException;
import com.jtech.urlshortener.exception.UrlNotFoundException;
import com.jtech.urlshortener.model.UrlEntity;
import com.jtech.urlshortener.model.UrlRequestDto;
import com.jtech.urlshortener.repository.UrlRepository;
import com.jtech.urlshortener.util.UrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final UrlValidator urlValidator;
    private final ClickAnalyticsService analyticsService;

    private static final int MAX_RETRIES = 3;
    private static final int DEFAULT_TTL_DAYS = 30;

    /**
     * Create a shortened URL
     */
    @Transactional
    public String shortenUrl(UrlRequestDto request) {
        log.info("Creating short URL for: {}", request.getLongUrl());

        // 1. Validate URL
        String sanitizedUrl = urlValidator.sanitizeUrl(request.getLongUrl());
        if (!urlValidator.isValidFormat(sanitizedUrl)) {
            throw new IllegalArgumentException("Invalid URL format: " + request.getLongUrl());
        }

        // 2. Check if custom alias is provided
        if (request.getCustomAlias() != null && !request.getCustomAlias().isEmpty()) {
            return createWithCustomAlias(sanitizedUrl, request);
        }

        // 3. Generate unique short code
        String shortCode = generateUniqueShortCode();

        // 4. Create entity
        UrlEntity entity = UrlEntity.builder()
                .longUrl(sanitizedUrl)
                .shortCode(shortCode)
                .expiresAt(calculateExpiryDate(request.getExpiresAt()))
                .clickCount(new java.util.concurrent.atomic.AtomicLong(0))
                .isActive(true)
                .build();

        // 5. Save to database
        UrlEntity saved = urlRepository.save(entity);
        log.info("Created short URL: {} -> {}", saved.getShortCode(), saved.getLongUrl());

        return saved.getShortCode();
    }

    /**
     * Create short URL with custom alias
     */
    private String createWithCustomAlias(String longUrl, UrlRequestDto request) {
        String customAlias = request.getCustomAlias();

        // Validate custom alias format (alphanumeric only)
        if (!customAlias.matches("^[a-zA-Z0-9]{4,20}$")) {
            throw new IllegalArgumentException("Custom alias must be 4-20 alphanumeric characters");
        }

        // Check if alias already exists
        if (urlRepository.existsByShortCode(customAlias)) {
            throw new DuplicateCodeException("Custom alias already taken: " + customAlias);
        }

        UrlEntity entity = UrlEntity.builder()
                .longUrl(longUrl)
                .shortCode(customAlias)
                .expiresAt(calculateExpiryDate(request.getExpiresAt()))
                .clickCount(new java.util.concurrent.atomic.AtomicLong(0))
                .isActive(true)
                .build();

        UrlEntity saved = urlRepository.save(entity);
        log.info("Created short URL with custom alias: {} -> {}", saved.getShortCode(), saved.getLongUrl());

        return saved.getShortCode();
    }

    /**
     * Generate unique short code with retry mechanism
     */
    private String generateUniqueShortCode() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Generate using timestamp + random for uniqueness
                long uniqueId = System.nanoTime() ^
                        java.util.concurrent.ThreadLocalRandom.current().nextLong();
                String shortCode = base62Encoder.encode(Math.abs(uniqueId % 1_000_000_000L));

                // Verify uniqueness in database
                if (!urlRepository.existsByShortCode(shortCode)) {
                    return shortCode;
                }

                log.warn("Collision detected for code: {}, retrying... (attempt {})", shortCode, attempt);

            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate key violation, retrying... (attempt {})", attempt);
                if (attempt == MAX_RETRIES) {
                    throw new DuplicateCodeException("Failed to generate unique code after " + MAX_RETRIES + " attempts");
                }
            }
        }
        throw new DuplicateCodeException("Unable to generate unique short code");
    }

    /**
     * Get original URL by short code (with caching)
     */
    @Cacheable(value = "urls", key = "#shortCode", unless = "#result == null")
    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortCode) {
        log.debug("Fetching original URL for code: {}", shortCode);

        UrlEntity entity = urlRepository.findActiveByShortCode(shortCode, LocalDateTime.now())
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));

        // Async click tracking - doesn't block the redirect
        CompletableFuture.runAsync(() -> {
            try {
                urlRepository.incrementClickCount(shortCode);
                analyticsService.recordClick(shortCode, entity.getLongUrl());
                log.debug("Recorded click for: {}", shortCode);
            } catch (Exception e) {
                log.error("Failed to record click: {}", e.getMessage());
            }
        });

        return entity.getLongUrl();
    }

    /**
     * Get URL info (for analytics)
     */
    @Transactional(readOnly = true)
    public UrlEntity getUrlInfo(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));
    }

    /**
     * Update long URL for existing short code
     */
    @CacheEvict(value = "urls", key = "#shortCode")
    @Transactional
    public UrlEntity updateUrl(String shortCode, String newLongUrl) {
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));

        String sanitizedUrl = urlValidator.sanitizeUrl(newLongUrl);
        if (!urlValidator.isValidFormat(sanitizedUrl)) {
            throw new IllegalArgumentException("Invalid URL format: " + newLongUrl);
        }

        entity.setLongUrl(sanitizedUrl);
        UrlEntity updated = urlRepository.save(entity);
        log.info("Updated URL: {} -> {}", shortCode, sanitizedUrl);

        return updated;
    }

    /**
     * Delete (soft delete) short URL
     */
    @CacheEvict(value = "urls", key = "#shortCode")
    @Transactional
    public void deleteUrl(String shortCode) {
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));

        entity.setActive(false);
        urlRepository.save(entity);
        log.info("Soft deleted URL: {}", shortCode);
    }

    /**
     * Permanently delete expired URLs (scheduled job)
     */
    @Transactional
    public int cleanupExpiredUrls() {
        int deleted = urlRepository.deactivateExpiredUrls(LocalDateTime.now());
        log.info("Cleaned up {} expired URLs", deleted);
        return deleted;
    }

    /**
     * Calculate expiry date (default 30 days if not specified)
     */
    private LocalDateTime calculateExpiryDate(LocalDateTime requestedExpiry) {
        if (requestedExpiry != null && requestedExpiry.isAfter(LocalDateTime.now())) {
            return requestedExpiry;
        }
        return LocalDateTime.now().plusDays(DEFAULT_TTL_DAYS);
    }

    /**
     * Get click count for a short URL
     */
    public long getClickCount(String shortCode) {
        UrlEntity entity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));
        return entity.getClickCount().get();
    }
}