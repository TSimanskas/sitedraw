package com.construction.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class JwtSecretValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);
    private static final String DEV_PREFIX = "dev-only";

    private final AppProperties appProperties;
    private final Environment environment;

    public JwtSecretValidator(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String secret = appProperties.jwt().secret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret / APP_JWT_SECRET must be at least 32 characters");
        }
        if (secret.startsWith(DEV_PREFIX) && !environment.matchesProfiles("dev")) {
            log.warn("Using the default development JWT secret. Set APP_JWT_SECRET before exposing this profile.");
        }
    }
}
