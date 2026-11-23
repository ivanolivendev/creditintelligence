package com.creditintelligence;

import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class PostgresIntegrationTest {
    static {
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            System.setProperty("docker.host", "npipe:////./pipe/dockerDesktopLinuxEngine");
        }
    }

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.6-alpine")
            .withDatabaseName("credit_intelligence")
            .withUsername("credit_app")
            .withPassword("local_demo_only_change_me");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("document.storage.root", () -> Path.of("target", "test-documents")
                .toAbsolutePath().toString());
    }

    static Path sharedFixture(String filename) {
        var local = Path.of("fixtures", "documents", filename);
        if (Files.exists(local)) {
            return local;
        }
        return Path.of("..", "fixtures", "documents", filename);
    }
}
