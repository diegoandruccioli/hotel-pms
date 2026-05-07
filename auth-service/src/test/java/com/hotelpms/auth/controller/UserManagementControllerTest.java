package com.hotelpms.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.dto.CreateUserRequest;
import com.hotelpms.auth.dto.UserResponse;
import com.hotelpms.auth.exception.DuplicateResourceException;
import com.hotelpms.auth.exception.GlobalExceptionHandler;
import com.hotelpms.auth.exception.NotFoundException;
import com.hotelpms.auth.service.UserManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    private static final String BASE_URL = "/api/v1/auth/users";
    private static final String PATH_DEACTIVATE = "/{userId}/deactivate";
    private static final String PATH_ACTIVATE = "/{userId}/activate";
    private static final String HEADER_HOTEL = "X-Auth-Hotel";
    private static final String ADMIN_USERNAME = "adminuser";
    private static final String NEW_USERNAME = "newuser";
    private static final String USER_EMAIL = "user@example.com";
    private static final UUID HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private UserManagementController userManagementController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserResponse userResponse;
    private UsernamePasswordAuthenticationToken adminAuth;

    @BeforeEach
    void setUp() {
        adminAuth = new UsernamePasswordAuthenticationToken(ADMIN_USERNAME, "", List.of());
        SecurityContextHolder.getContext().setAuthentication(adminAuth);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(userManagementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        userResponse = new UserResponse(
                USER_ID, NEW_USERNAME, USER_EMAIL,
                Role.RECEPTIONIST.name(), true, true, LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListUsersReturn200() throws Exception {
        when(userManagementService.listUsers(HOTEL_ID)).thenReturn(List.of(userResponse));

        mockMvc.perform(get(BASE_URL).header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value(NEW_USERNAME));
    }

    @Test
    void shouldCreateUserReturn201() throws Exception {
        final CreateUserRequest request = new CreateUserRequest(NEW_USERNAME, "password123", USER_EMAIL, Role.RECEPTIONIST);
        when(userManagementService.createUser(eq(HOTEL_ID), any(CreateUserRequest.class)))
                .thenReturn(userResponse);

        mockMvc.perform(post(BASE_URL)
                        .header(HEADER_HOTEL, HOTEL_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(NEW_USERNAME))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void shouldCreateUserReturn400WhenPayloadInvalid() throws Exception {
        final String body = "{\"username\":\"\",\"password\":\"short\",\"email\":\"bad\",\"role\":\"RECEPTIONIST\"}";

        mockMvc.perform(post(BASE_URL)
                        .header(HEADER_HOTEL, HOTEL_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateUserReturn409WhenUsernameAlreadyExists() throws Exception {
        final CreateUserRequest request = new CreateUserRequest(NEW_USERNAME, "password123", USER_EMAIL, Role.RECEPTIONIST);
        when(userManagementService.createUser(eq(HOTEL_ID), any(CreateUserRequest.class)))
                .thenThrow(new DuplicateResourceException("USERNAME_ALREADY_EXISTS"));

        mockMvc.perform(post(BASE_URL)
                        .header(HEADER_HOTEL, HOTEL_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldDeactivateUserReturn200() throws Exception {
        final UserResponse deactivatedResponse = new UserResponse(
                USER_ID, NEW_USERNAME, USER_EMAIL, Role.RECEPTIONIST.name(), false, true, LocalDateTime.now());
        when(userManagementService.deactivateUser(HOTEL_ID, USER_ID, ADMIN_USERNAME))
                .thenReturn(deactivatedResponse);

        mockMvc.perform(patch(BASE_URL + PATH_DEACTIVATE, USER_ID)
                        .with(req -> {
                            req.setUserPrincipal(adminAuth);
                            return req;
                        })
                        .header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldDeactivateUserReturn404WhenNotFound() throws Exception {
        when(userManagementService.deactivateUser(HOTEL_ID, USER_ID, ADMIN_USERNAME))
                .thenThrow(new NotFoundException("USER_NOT_FOUND"));

        mockMvc.perform(patch(BASE_URL + PATH_DEACTIVATE, USER_ID)
                        .with(req -> {
                            req.setUserPrincipal(adminAuth);
                            return req;
                        })
                        .header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeactivateUserReturn409WhenDeactivatingSelf() throws Exception {
        when(userManagementService.deactivateUser(HOTEL_ID, USER_ID, ADMIN_USERNAME))
                .thenThrow(new IllegalStateException("CANNOT_DEACTIVATE_SELF"));

        mockMvc.perform(patch(BASE_URL + PATH_DEACTIVATE, USER_ID)
                        .with(req -> {
                            req.setUserPrincipal(adminAuth);
                            return req;
                        })
                        .header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldActivateUserReturn200() throws Exception {
        final UserResponse activatedResponse = new UserResponse(
                USER_ID, NEW_USERNAME, USER_EMAIL, Role.RECEPTIONIST.name(), true, false, LocalDateTime.now());
        when(userManagementService.activateUser(HOTEL_ID, USER_ID)).thenReturn(activatedResponse);

        mockMvc.perform(patch(BASE_URL + PATH_ACTIVATE, USER_ID)
                        .header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldActivateUserReturn404WhenNotFound() throws Exception {
        when(userManagementService.activateUser(HOTEL_ID, USER_ID))
                .thenThrow(new NotFoundException("USER_NOT_FOUND"));

        mockMvc.perform(patch(BASE_URL + PATH_ACTIVATE, USER_ID)
                        .header(HEADER_HOTEL, HOTEL_ID.toString()))
                .andExpect(status().isNotFound());
    }
}
