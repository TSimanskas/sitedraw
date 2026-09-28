package com.construction.platform.storage;

import java.io.InputStream;

public interface StorageService {

    void upload(String key, InputStream inputStream, long contentLength, String contentType);

    void copy(String sourceKey, String destinationKey);

    InputStream download(String key);

    boolean exists(String key);

    void delete(String key);
}
