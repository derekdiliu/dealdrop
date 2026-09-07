package io.github.derekdiliu.dealdrop.catalog;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real Postgres, plus S3Mock standing in for Cloudflare R2 so tests need no cloud credentials.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String BUCKET = "dealdrop-test";
    public static final String PUBLIC_BASE_URL = "https://images.example.test";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    GenericContainer<?> s3MockContainer() {
        return new GenericContainer<>("adobe/s3mock:5.2.0")
                .withExposedPorts(9090)
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET);
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> s3MockContainer) {
        return registry -> {
            registry.add("dealdrop.storage.endpoint",
                    () -> "http://" + s3MockContainer.getHost() + ":" + s3MockContainer.getMappedPort(9090));
            registry.add("dealdrop.storage.bucket", () -> BUCKET);
            registry.add("dealdrop.storage.access-key-id", () -> "test");
            registry.add("dealdrop.storage.secret-access-key", () -> "test");
            registry.add("dealdrop.storage.public-base-url", () -> PUBLIC_BASE_URL);
        };
    }
}
