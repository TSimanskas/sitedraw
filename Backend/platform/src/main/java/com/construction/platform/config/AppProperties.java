package com.construction.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Storage storage,
        Cors cors
) {

    public record Jwt(String secret, long expirationMs) {}

    public record Storage(String type, Local local, Minio minio) {

        public record Local(String basePath) {}

        public record Minio(
                String endpoint,
                String accessKey,
                String secretKey,
                String bucket,
                String region
        ) {}
    }

    public record Cors(String allowedOrigins) {}
}
