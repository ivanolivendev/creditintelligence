package com.creditintelligence.document.application;

public interface DocumentStorage {
    void put(String key, byte[] content);
    void delete(String key);
}
