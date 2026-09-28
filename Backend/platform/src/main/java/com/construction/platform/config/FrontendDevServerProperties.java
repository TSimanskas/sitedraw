package com.construction.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.frontend.dev-server")
public record FrontendDevServerProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("frontend") String directory,
        @DefaultValue("5173") int port,
        String npmCommand
) {
}
