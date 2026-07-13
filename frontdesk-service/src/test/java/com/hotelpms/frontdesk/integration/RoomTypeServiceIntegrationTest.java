package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeRequest;
import com.hotelpms.frontdesk.rooms.dto.RoomTypeResponse;
import com.hotelpms.frontdesk.rooms.service.RoomTypeService;
import com.hotelpms.frontdesk.stays.security.AlloggiatiCredentialEncryptor;
import com.hotelpms.frontdesk.stays.service.AlloggiatiWebSenderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for RoomTypeService against a real PostgreSQL database.
 * Flyway applies all frontdesk migrations at context startup.
 * Each test rolls back via {@code @Transactional} for state isolation.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:configserver:",
                "CONFIG_SERVER_PASSWORD=ci-test-placeholder-only",
                "INTERNAL_HMAC_SECRET=test-integration-secret-only",
                "internal.hmac.secret=test-integration-secret-only",
                "management.tracing.enabled=false",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                "spring.cache.type=none"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RoomTypeServiceIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("hotel_frontdesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static final String SUITE_NAME = "Standard Suite";
    private static final int MAX_OCCUPANCY = 2;
    private static final BigDecimal BASE_PRICE = new BigDecimal("120.00");

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private GuestClient guestClient;

    @MockitoBean
    private BillingClient billingClient;

    @MockitoBean
    private AlloggiatiWebSenderService alloggiatiWebSenderService;

    @MockitoBean
    private AlloggiatiCredentialEncryptor alloggiatiCredentialEncryptor;

    @Autowired
    private RoomTypeService roomTypeService;

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    @DisplayName("Context loads — all Flyway migrations apply without error")
    void contextLoadsAndFlywayMigrationsApplied() {
        assertNotNull(roomTypeService, "RoomTypeService must be present in application context");
    }

    @Test
    @DisplayName("Created room type can be retrieved by id with correct attributes")
    void createAndRetrieveRoomTypeById() {
        final RoomTypeRequest request =
                new RoomTypeRequest(SUITE_NAME, "A comfortable suite", MAX_OCCUPANCY, BASE_PRICE);

        final RoomTypeResponse created = roomTypeService.createRoomType(request);

        assertNotNull(created.id());
        final RoomTypeResponse retrieved = roomTypeService.getRoomTypeById(created.id());
        assertEquals(SUITE_NAME, retrieved.name());
        assertEquals(MAX_OCCUPANCY, retrieved.maxOccupancy());
        assertEquals(0, BASE_PRICE.compareTo(retrieved.basePrice()));
    }

    @Test
    @DisplayName("getAllRoomTypes returns all active room types including newly created ones")
    void createdRoomTypesAreReturnedByGetAll() {
        roomTypeService.createRoomType(
                new RoomTypeRequest("Deluxe Room", "Deluxe with view", 2, new BigDecimal("95.00")));
        roomTypeService.createRoomType(
                new RoomTypeRequest("Family Suite", "Large family suite", 4, new BigDecimal("180.00")));

        final List<RoomTypeResponse> all = roomTypeService.getAllRoomTypes();

        assertEquals(2, all.size(), "Expected exactly the two room types created in this test");
    }

    @Test
    @DisplayName("Soft-deleted room type is excluded from getAllRoomTypes")
    void softDeletedRoomTypeIsExcluded() {
        final RoomTypeResponse created = roomTypeService.createRoomType(
                new RoomTypeRequest("Economy Room", "Basic room", 1, new BigDecimal("60.00")));
        roomTypeService.deleteRoomType(created.id());

        final List<RoomTypeResponse> all = roomTypeService.getAllRoomTypes();

        assertEquals(0, all.size(), "Soft-deleted room type must not appear in listing");
    }
}
