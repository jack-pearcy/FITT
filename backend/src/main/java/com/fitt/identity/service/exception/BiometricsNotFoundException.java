package com.fitt.identity.service.exception;

public class BiometricsNotFoundException extends RuntimeException {

    public BiometricsNotFoundException(String message) {
        super(message);
    }
}