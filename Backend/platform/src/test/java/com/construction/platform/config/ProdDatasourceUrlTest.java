package com.construction.platform.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdDatasourceUrlTest {

    @Test
    void jdbcUrlKeepsHostAndForcesSslWithoutGss() {
        String jdbc = ProdDatasourceUrl.normalize(
                "jdbc:postgresql://dpg-example.frankfurt-postgres.render.com:5432/sitedraw_db");
        assertEquals(
                "jdbc:postgresql://dpg-example.frankfurt-postgres.render.com:5432/sitedraw_db?sslmode=require&gssEncMode=disable",
                jdbc);
    }

    @Test
    void renderPostgresUrlBecomesJdbc() {
        String jdbc = ProdDatasourceUrl.normalize(
                "postgres://user:secret@dpg-example-a/sitedraw_db");
        assertEquals(
                "jdbc:postgresql://user:secret@dpg-example-a/sitedraw_db?sslmode=require&gssEncMode=disable",
                jdbc);
    }

    @Test
    void existingQueryParamsAreReplacedNotDuplicated() {
        String jdbc = ProdDatasourceUrl.normalize(
                "jdbc:postgresql://host:5432/db?sslmode=require");
        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require&gssEncMode=disable", jdbc);
    }

    @Test
    void blankIsIgnored() {
        assertNull(ProdDatasourceUrl.normalize("  "));
    }

    @Test
    void logHostStripsUserInfo() {
        assertEquals(
                "dpg-example-a:5432",
                ProdDatasourceUrl.hostForLog("jdbc:postgresql://user:pass@dpg-example-a:5432/db?sslmode=require"));
    }

    @Test
    void publicRenderHostRewritesToPrivateHostname() {
        String internal = ProdDatasourceUrl.toInternalJdbcUrl(
                "jdbc:postgresql://dpg-example-a.frankfurt-postgres.render.com:5432/sitedraw_db?sslmode=require");
        assertEquals(
                "jdbc:postgresql://dpg-example-a:5432/sitedraw_db?sslmode=require",
                internal);
    }

    @Test
    void candidatesTryPrivateHostWithoutSslFirst() {
        var urls = ProdDatasourceUrl.candidates(
                "jdbc:postgresql://dpg-example-a.frankfurt-postgres.render.com:5432/sitedraw_db");
        assertEquals("dpg-example-a:5432", ProdDatasourceUrl.hostForLog(urls.get(0)));
        assertTrue(urls.get(0).contains("sslmode=disable"));
        assertEquals(
                "dpg-example-a.frankfurt-postgres.render.com:5432",
                ProdDatasourceUrl.hostForLog(urls.get(urls.size() - 1)));
    }
}
