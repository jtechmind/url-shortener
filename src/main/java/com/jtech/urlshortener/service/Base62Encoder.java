package com.jtech.urlshortener.service;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;
    private static final int CODE_LENGTH = 6;

    public String encode(long id) {
        if (id == 0) return String.valueOf(BASE62_CHARS.charAt(0));

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62_CHARS.charAt((int)(id % BASE)));
            id /= BASE;
        }

        // Pad to desired length
        while (sb.length() < CODE_LENGTH) {
            sb.append(BASE62_CHARS.charAt(0));
        }

        return sb.reverse().toString();
    }

    public long decode(String shortCode) {
        long result = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            result = result * BASE + BASE62_CHARS.indexOf(shortCode.charAt(i));
        }
        return result;
    }
}
