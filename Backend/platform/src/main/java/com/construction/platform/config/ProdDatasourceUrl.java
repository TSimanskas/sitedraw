package com.construction.platform.config;

/**
 * Render (and other hosts) inject postgres:// URLs. Spring needs jdbc:postgresql://
 * plus sslmode=require. Cloud proxies also hang up if the driver tries GSS first.
 */
final class ProdDatasourceUrl {

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
        int queryAt = url.indexOf('?');
        String base = queryAt >= 0 ? url.substring(0, queryAt) : url;
        return base + "?sslmode=require&gssEncMode=disable";
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
