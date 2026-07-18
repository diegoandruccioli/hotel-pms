package com.hotelpms.frontdesk.integration;

import com.hotelpms.frontdesk.client.BillingClient;
import com.hotelpms.frontdesk.client.GuestClient;
import com.hotelpms.frontdesk.exception.NotFoundException;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for RoomTypeService against a real PostgreSQL database.
 * Flyway applies all frontdesk migrations at context startup (including V7,
 * which adds {@code hotel_id} to {@code room_types} for T-ROOM-02).
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
                "management.health.redis.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
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
    private static final UUID HOTEL_ID = UUID.randomUUID();
    private static final UUID OTHER_HOTEL_ID = UUID.randomUUID();

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

        final RoomTypeResponse created = roomTypeService.createRoomType(request, HOTEL_ID);

        assertNotNull(created.id());
        final RoomTypeResponse retrieved = roomTypeService.getRoomTypeById(created.id(), HOTEL_ID);
        assertEquals(SUITE_NAME, retrieved.name());
        assertEquals(MAX_OCCUPANCY, retrieved.maxOccupancy());
        assertEquals(0, BASE_PRICE.compareTo(retrieved.basePrice()));
    }

    @Test
    @DisplayName("getAllRoomTypes returns all active room types including newly created ones")
    void createdRoomTypesAreReturnedByGetAll() {
        roomTypeService.createRoomType(
                new RoomTypeRequest("Deluxe Room", "Deluxe with view", 2, new BigDecimal("95.00")), HOTEL_ID);
        roomTypeService.createRoomType(
                new RoomTypeRequest("Family Suite", "Large family suite", 4, new BigDecimal("180.00")), HOTEL_ID);

        final List<RoomTypeResponse> all = roomTypeService.getAllRoomTypes(HOTEL_ID);

        assertEquals(2, all.size(), "Expected exactly the two room types created in this test");
    }

    @Test
    @DisplayName("Soft-deleted room type is excluded from getAllRoomTypes")
    void softDeletedRoomTypeIsExcluded() {
        final RoomTypeResponse created = roomTypeService.createRoomType(
                new RoomTypeRequest("Economy Room", "Basic room", 1, new BigDecimal("60.00")), HOTEL_ID);
        roomTypeService.deleteRoomType(created.id(), HOTEL_ID);

        final List<RoomTypeResponse> all = roomTypeService.getAllRoomTypes(HOTEL_ID);

        assertEquals(0, all.size(), "Soft-deleted room type must not appear in listing");
    }

    @Test
    @DisplayName("T-ROOM-02: two independent hotels can both name a room type the same")
    void twoHotelsCanUseTheSameRoomTypeName() {
        roomTypeService.createRoomType(
                new RoomTypeRequest(SUITE_NAME, "Hotel A's suite", MAX_OCCUPANCY, BASE_PRICE), HOTEL_ID);

        final RoomTypeResponse otherHotelSuite = roomTypeService.createRoomType(
                new RoomTypeRequest(SUITE_NAME, "Hotel B's suite", MAX_OCCUPANCY, BASE_PRICE), OTHER_HOTEL_ID);

        assertNotNull(otherHotelSuite.id(),
                "The same room type name must be usable by a different hotel (per-hotel UNIQUE, not global)");
    }

    @Test
    @DisplayName("T-ROOM-02: a room type from another hotel is never visible or writable")
    void roomTypeIsNotVisibleToADifferentHotel() {
        final RoomTypeResponse created = roomTypeService.createRoomType(
                new RoomTypeRequest(SUITE_NAME, "A comfortable suite", MAX_OCCUPANCY, BASE_PRICE), HOTEL_ID);

        assertThrows(NotFoundException.class,
                () -> roomTypeService.getRoomTypeById(created.id(), OTHER_HOTEL_ID));
        assertThrows(NotFoundException.class,
                () -> roomTypeService.updateRoomType(created.id(), OTHER_HOTEL_ID,
                        new RoomTypeRequest("Tampered", "Tampered", 1, BigDecimal.ONE)));
        assertThrows(NotFoundException.class,
                () -> roomTypeService.deleteRoomType(created.id(), OTHER_HOTEL_ID));

        // The original room type, from the real owning hotel, must be untouched.
        final RoomTypeResponse untouched = roomTypeService.getRoomTypeById(created.id(), HOTEL_ID);
        assertEquals(SUITE_NAME, untouched.name());
        assertEquals(0, BASE_PRICE.compareTo(untouched.basePrice()));
    }
}
