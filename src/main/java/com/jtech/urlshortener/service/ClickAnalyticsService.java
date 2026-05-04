package com.jtech.urlshortener.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ClickAnalyticsService {
    private final Map<String, AtomicLong> clickCounts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastClickTimes = new ConcurrentHashMap<>();

    public void recordClick(String shortCode, String longUrl) {
        clickCounts.computeIfAbsent(shortCode, k -> new AtomicLong(0)).incrementAndGet();
        lastClickTimes.put(shortCode, LocalDateTime.now());
    }

    public long getClickCount(String shortCode) {
        return clickCounts.getOrDefault(shortCode, new AtomicLong(0)).get();
    }

    public LocalDateTime getLastClickTime(String shortCode) {
        return lastClickTimes.get(shortCode);
    }
}
