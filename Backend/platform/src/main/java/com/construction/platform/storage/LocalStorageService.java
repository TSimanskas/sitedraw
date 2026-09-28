package com.construction.platform.storage;

import com.construction.platform.config.AppProperties;
import com.construction.platform.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path basePath;

    public LocalStorageService(AppProperties appProperties) {
        this.basePath = Path.of(appProperties.storage().local().basePath()).toAbsolutePath().normalize();
    }

    @Override
    public void upload(String key, InputStream inputStream, long contentLength, String contentType) {
        Path destination = resolveKey(key);
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        Path source = resolveKey(sourceKey);
        Path destination = resolveKey(destinationKey);

        if (!Files.exists(source)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Source file not found");
        }

        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy file");
        }
    }

    @Override
    public InputStream download(String key) {
        Path file = resolveKey(key);
        if (!Files.exists(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }

        try {
            return Files.newInputStream(file);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolveKey(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveKey(key));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file");
        }
    }

    private Path resolveKey(String key) {
        Path resolved = basePath.resolve(key).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid storage key");
        }
        return resolved;
    }
}
