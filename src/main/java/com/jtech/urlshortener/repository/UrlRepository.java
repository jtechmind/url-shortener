package com.jtech.urlshortener.repository;

import com.jtech.urlshortener.model.UrlEntity;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlEntity, Long> {
    Optional<UrlEntity> findByShortCode(String shortCode);

    // Atomic increment using native query
    @Modifying
    @Transactional
    @Query("UPDATE UrlEntity u SET u.clickCount = u.clickCount + 1 WHERE u.shortCode = :shortCode")
    void incrementClickCount(@Param("shortCode") String shortCode);

    @Query("SELECT u FROM UrlEntity u WHERE u.shortCode = :shortCode AND u.expiresAt > :now AND u.isActive = true")
    Optional<UrlEntity> findActiveByShortCode(@Param("shortCode") String shortCode, @Param("now") LocalDateTime now);

    long countByCreatedAtAfter(LocalDateTime date);
}
