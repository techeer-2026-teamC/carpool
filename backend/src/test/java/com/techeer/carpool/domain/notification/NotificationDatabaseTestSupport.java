package com.techeer.carpool.domain.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeer.carpool.domain.notification.entity.Notification;
import com.techeer.carpool.domain.notification.outbox.NotificationOutbox;
import com.techeer.carpool.domain.notification.outbox.NotificationOutboxRepository;
import com.techeer.carpool.domain.notification.repository.NotificationRepository;
import com.techeer.carpool.domain.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
abstract class NotificationDatabaseTestSupport {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getenv().getOrDefault("MOA_POSTGIS_IMAGE", "postgis/postgis:15-3.5")).asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url", postgres::getJdbcUrl);
        props.add("spring.datasource.username", postgres::getUsername);
        props.add("spring.datasource.password", postgres::getPassword);
        props.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Notification.class, NotificationOutbox.class})
    @EnableJpaRepositories(basePackageClasses = {NotificationRepository.class, NotificationOutboxRepository.class})
    @Import(NotificationService.class)
    static class DatabaseConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationOutboxRepository outboxes;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clearNotifications() { outboxes.deleteAll(); notifications.deleteAll(); }
}
