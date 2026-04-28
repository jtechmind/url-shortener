package com.jtech.urlshortener.service;

import com.jtech.urlshortener.model.UrlEntity;
import com.jtech.urlshortener.model.UrlRequestDto;
import com.jtech.urlshortener.repository.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.constraintvalidators.hv.URLValidator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {
    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final URLValidator urlValidator;
    private static final int MAX_RETRIES = 3;

    @Transactional
    public String shortenUrl(UrlRequestDto request) {
        // Validate URL format
        if (!urlValidator.isValid(request.getLongUrl())) {
            throw new IllegalArgumentException("Invalid URL format");
        }

        UrlEntity urlEntity = UrlEntity.builder()
                .longUrl(request.getLongUrl())
                .shortCode(generateUniqueShortCode())
                .expiresAt(request.getExpiresAt())
                .clickCount(new java.util.concurrent.atomic.AtomicLong(0))
                .build();

        UrlEntity saved = urlRepository.save(urlEntity);
        log.info("Created short URL: {} -> {}", saved.getShortCode(), saved.getLongUrl());

        return saved.getShortCode();
    }

    private String generateUniqueShortCode() {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Use DB sequence for distributed uniqueness
                long sequenceValue = System.nanoTime() % 1000000;
                String shortCode = base62Encoder.encode(sequenceValue);

                // Verify uniqueness
                if (!urlRepository.findByShortCode(shortCode).isPresent()) {
                    return shortCode;
                }
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate code generated, retrying... Attempt: {}", attempt);
                if (attempt == MAX_RETRIES) {
                    throw new DuplicateCodeException("Failed to generate unique code after " + MAX_RETRIES + " attempts");
                }
            }
        }
        throw new DuplicateCodeException("Unable to generate unique short code");
    }

    @Cacheable(value = "urls", key = "#shortCode", unless = "#result == null")
    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortCode) {
        log.debug("Fetching URL from database for code: {}", shortCode);

        UrlEntity urlEntity = urlRepository.findActiveByShortCode(shortCode, LocalDateTime.now())
                .orElseThrow(() -> new UrlNotFoundException("URL not found for code: " + shortCode));

        // Async click tracking (non-blocking)
        CompletableFuture.runAsync(() -> {
            urlRepository.incrementClickCount(shortCode);
            log.debug("Incremented click count for: {}", shortCode);
        });

        return urlEntity.getLongUrl();
    }

    @CacheEvict(value = "urls", key = "#shortCode")
    @Transactional
    public void deleteUrl(String shortCode) {
        UrlEntity urlEntity = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found: " + shortCode));
        urlEntity.setActive(false);
        urlRepository.save(urlEntity);
        log.info("Deleted/disabled URL: {}", shortCode);
    }
}
