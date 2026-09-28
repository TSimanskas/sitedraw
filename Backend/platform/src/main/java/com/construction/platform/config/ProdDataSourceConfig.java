package com.construction.platform.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
@Profile("prod")
public class ProdDataSourceConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        String rawUrl = firstNonBlank(
                environment.getProperty("SPRING_DATASOURCE_URL"),
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("spring.datasource.url"));
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        List<String> urls = ProdDatasourceUrl.candidates(rawUrl);
        if (urls.isEmpty()) {
            throw new IllegalStateException("SPRING_DATASOURCE_URL or DATABASE_URL is missing");
        }
        logProbe(urls, username, password);
        Exception last = null;
        for (String url : urls) {
            String host = ProdDatasourceUrl.hostForLog(url);
            System.out.println("SiteDraw trying Postgres " + host);
            HikariDataSource pool = null;
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(url);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriverClassName("org.postgresql.Driver");
                config.setMaximumPoolSize(5);
                config.setConnectionTimeout(8_000);
                pool = new HikariDataSource(config);
                System.out.println("SiteDraw connected to Postgres via " + host);
                return pool;
            } catch (Exception ex) {
                last = ex;
                System.out.println("SiteDraw Postgres failed (" + host + "): " + rootMessage(ex));
                if (pool != null) {
                    pool.close();
                }
            }
        }
        throw new IllegalStateException(
                "Could not open Postgres. Use the Internal hostname from the database Connect menu, "
                        + "and confirm the web service region matches the database.",
                last);
    }

    private static void logProbe(List<String> urls, String username, String password) {
        System.out.println("SiteDraw datasource user=" + username
                + " passwordChars=" + (password == null ? 0 : password.length()));
        List<String> hosts = new ArrayList<>(new LinkedHashSet<>(urls.stream()
                .map(ProdDatasourceUrl::hostName)
                .toList()));
        for (String host : hosts) {
            try {
                System.out.println("SiteDraw DNS " + host + " -> " + InetAddress.getByName(host).getHostAddress());
            } catch (Exception ex) {
                System.out.println("SiteDraw DNS " + host + " FAILED: " + ex.getMessage());
            }
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
