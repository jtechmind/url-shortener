package com.jtech.urlshortener.util;

import org.springframework.stereotype.Component;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

@Component
public class UrlValidator {

    // URL regex pattern
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?" +                                         // protocol (optional)
                    "(([\\da-z.-]+)\\.([a-z.]{2,6})|" +                       // domain name
                    "((\\d{1,3}\\.){3}\\d{1,3}))" +                           // OR IP address
                    "(:\\d+)?" +                                              // port (optional)
                    "(/[/\\w .-]*)*" +                                        // path
                    "(\\?[;&\\w .-]+)?" +                                     // query string (optional)
                    "(#[\\w .-]+)?$",                                         // fragment (optional)
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Validate URL format
     */
    public boolean isValidFormat(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(url).matches();
    }

    /**
     * Check if URL is reachable (optional, can be disabled for performance)
     */
    public boolean isReachable(String urlString, int timeoutMs) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(timeoutMs);
            connection.connect();
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false; // Not reachable, but we'll still allow it
        }
    }

    /**
     * Sanitize URL (add https:// if missing)
     */
    public String sanitizeUrl(String url) {
        if (url == null) return null;

        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }
}