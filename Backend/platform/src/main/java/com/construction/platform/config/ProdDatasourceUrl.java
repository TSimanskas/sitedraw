package com.construction.platform.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Render injects postgres:// URLs. Spring needs jdbc:postgresql:// plus sslmode=require.
 * Cloud proxies hang up if the driver tries GSS first. Connecting to the public
 * *.postgres.render.com host from a Render web service often dies during auth;
 * the private dpg-…-a hostname is the one that works on the same private network.
 */
final class ProdDatasourceUrl {

    private static final Pattern RENDER_PUBLIC_HOST =
            Pattern.compile("^(dpg-[^.]+)\\.[a-z0-9-]+-postgres\\.render\\.com$");
    private static final String SSL_OFF = "sslmode=disable&gssEncMode=disable";
    private static final String SSL_REQUIRE = "sslmode=require&gssEncMode=disable";
    private static final String SSL_PREFER = "sslmode=prefer&gssEncMode=disable";
    private static final String SSL_DIRECT = "sslmode=require&gssEncMode=disable&sslNegotiation=direct";

    private ProdDatasourceUrl() {
    }

    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String url = raw.trim();
        if (url.isEmpty()) {
            return null;
        }
        url = stripWrappingQuotes(url);
        url = toJdbc(url);
        return withQuery(url, SSL_REQUIRE);
    }

    static List<String> candidates(String raw) {
        String normalized = normalize(raw);
        Set<String> urls = new LinkedHashSet<>();
        if (normalized == null) {
            return List.of();
        }
        String internal = toInternalJdbcUrl(normalized);
        if (internal != null) {
            String named = replaceHost(internal, "sitedraw-db");
            urls.add(withQuery(named, SSL_OFF));
            urls.add(withQuery(named, SSL_PREFER));
            urls.add(withQuery(internal, SSL_OFF));
            urls.add(withQuery(internal, SSL_PREFER));
            urls.add(internal);
        }
        urls.add(withQuery(normalized, SSL_DIRECT));
        urls.add(normalized);
        return new ArrayList<>(urls);
    }

    static String toInternalJdbcUrl(String jdbcUrl) {
        String host = hostName(jdbcUrl);
        Matcher matcher = RENDER_PUBLIC_HOST.matcher(host);
        if (!matcher.matches()) {
            return null;
        }
        return jdbcUrl.replace(host, matcher.group(1));
    }

    static String replaceHost(String jdbcUrl, String newHost) {
        return jdbcUrl.replace(hostName(jdbcUrl), newHost);
    }

    static String hostForLog(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "(unset)";
        }
        String withoutQuery = jdbcUrl.split("\\?", 2)[0];
        int scheme = withoutQuery.indexOf("://");
        String rest = scheme >= 0 ? withoutQuery.substring(scheme + 3) : withoutQuery;
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }

    static String hostName(String jdbcUrl) {
        String hostPort = hostForLog(jdbcUrl);
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0) {
            return hostPort.substring(0, colon);
        }
        return hostPort;
    }

    private static String withQuery(String url, String query) {
        int queryAt = url.indexOf('?');
        String base = queryAt >= 0 ? url.substring(0, queryAt) : url;
        return base + "?" + query;
    }

    private static String toJdbc(String url) {
        if (url.startsWith("jdbc:postgresql://")) {
            return url;
        }
        if (url.startsWith("postgres://")) {
            return "jdbc:postgresql://" + url.substring("postgres://".length());
        }
        if (url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring("postgresql://".length());
        }
        return url;
    }

    private static String stripWrappingQuotes(String url) {
        if (url.length() >= 2 && url.startsWith("\"") && url.endsWith("\"")) {
            return url.substring(1, url.length() - 1);
        }
        return url;
    }
}
