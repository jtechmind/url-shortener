package com.jtech.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException{

    public  DuplicateCodeException(String message) {
        super(message);
    }

    public DuplicateCodeException(String message, Throwable cause) {
        super(message, cause);
    }
}

