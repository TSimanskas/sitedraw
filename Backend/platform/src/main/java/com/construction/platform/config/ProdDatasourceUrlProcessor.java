package com.construction.platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class ProdDatasourceUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isProd(environment)) {
            return;
        }
        String raw = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("spring.datasource.url"));
        String jdbc = ProdDatasourceUrl.normalize(raw);
        if (jdbc == null) {
            return;
        }
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", jdbc);
        environment.getPropertySources().addFirst(new MapPropertySource("prodDatasourceUrl", props));
        System.out.println("SiteDraw prod datasource host=" + ProdDatasourceUrl.hostForLog(jdbc));
    }

    private static boolean isProd(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        String fromEnv = environment.getProperty("SPRING_PROFILES_ACTIVE", "");
        return fromEnv.contains("prod");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
