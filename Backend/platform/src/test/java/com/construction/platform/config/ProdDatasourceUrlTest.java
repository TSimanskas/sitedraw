package com.construction.platform.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
