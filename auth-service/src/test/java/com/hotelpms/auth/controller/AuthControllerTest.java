package com.hotelpms.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotelpms.auth.domain.Role;
import com.hotelpms.auth.dto.AuthResponse;
import com.hotelpms.auth.dto.ChangePasswordRequest;
import com.hotelpms.auth.dto.LoginRequest;
import com.hotelpms.auth.dto.RegisterRequest;
import com.hotelpms.auth.exception.BadCredentialsException;
import com.hotelpms.auth.exception.GlobalExceptionHandler;
import com.hotelpms.auth.repository.UserAccountRepository;
import com.hotelpms.auth.service.AuthService;
import com.hotelpms.auth.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String BASE_URL = "/api/v1/auth";
    private static final String PATH_LOGIN = "/login";
    private static final String PATH_REGISTER = "/register";
    private static final String PATH_LOGOUT = "/logout";
    private static final String PATH_REFRESH = "/refresh";
    private static final String PATH_CHANGE_PASSWORD = "/change-password";
    private static final String PATH_ME = "/me";
    private static final String COOKIE_JWT = "jwt";
    private static final String COOKIE_REFRESH = "refresh_token";
    private static final String TEST_TOKEN = "test.jwt.token";
    private static final String TEST_REFRESH = "test.refresh.token";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String TEST_EMAIL = "test@example.com";
    private static final UUID TEST_HOTEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserAccountRepository userRepository;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();

        authResponse = new AuthResponse(TEST_TOKEN, TEST_REFRESH, false);
    }

    @Test
    void shouldLoginReturn200WithCookiesAndBody() throws Exception {
        final LoginRequest request = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post(BASE_URL + PATH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void shouldLoginReturn401OnBadCredentials() throws Exception {
        final LoginRequest request = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("INVALID_CREDENTIALS"));

        mockMvc.perform(post(BASE_URL + PATH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoginReturn400OnBlankUsername() throws Exception {
        final String body = "{\"username\":\"\",\"password\":\"password123\"}";

        mockMvc.perform(post(BASE_URL + PATH_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRegisterReturn201WithCookies() throws Exception {
        final RegisterRequest request = new RegisterRequest(
                TEST_USERNAME, TEST_PASSWORD, TEST_EMAIL, Role.RECEPTIONIST, TEST_HOTEL_ID);
        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post(BASE_URL + PATH_REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldRefreshReturn200WhenCookiePresent() throws Exception {
        when(authService.refresh(TEST_REFRESH)).thenReturn(authResponse);

        mockMvc.perform(post(BASE_URL + PATH_REFRESH)
                        .cookie(new Cookie(COOKIE_REFRESH, TEST_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldRefreshReturn401WhenCookieMissing() throws Exception {
        mockMvc.perform(post(BASE_URL + PATH_REFRESH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogoutReturn200AndClearCookies() throws Exception {
        mockMvc.perform(post(BASE_URL + PATH_LOGOUT)
                        .cookie(new Cookie(COOKIE_JWT, TEST_TOKEN))
                        .cookie(new Cookie(COOKIE_REFRESH, TEST_REFRESH)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldChangePasswordReturn200() throws Exception {
        final ChangePasswordRequest request = new ChangePasswordRequest("currentPw1", "newPassword1");
        when(jwtService.extractUsername(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.isTokenValid(TEST_TOKEN, TEST_USERNAME)).thenReturn(true);
        when(authService.changePassword(eq(TEST_USERNAME), any(ChangePasswordRequest.class)))
                .thenReturn(authResponse);

        mockMvc.perform(post(BASE_URL + PATH_CHANGE_PASSWORD)
                        .cookie(new Cookie(COOKIE_JWT, TEST_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void shouldChangePasswordReturn401WhenNoCookie() throws Exception {
        final ChangePasswordRequest request = new ChangePasswordRequest("currentPw1", "newPassword1");

        mockMvc.perform(post(BASE_URL + PATH_CHANGE_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGetMeReturn200WithUsernameAndRole() throws Exception {
        when(jwtService.extractUsername(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.extractClaim(eq(TEST_TOKEN), any())).thenReturn(ROLE_ADMIN);
        when(jwtService.isTokenValid(TEST_TOKEN, TEST_USERNAME)).thenReturn(true);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE_URL + PATH_ME)
                        .cookie(new Cookie(COOKIE_JWT, TEST_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.role").value(ROLE_ADMIN));
    }

    @Test
    void shouldGetMeReturn401WhenNoCookie() throws Exception {
        mockMvc.perform(get(BASE_URL + PATH_ME))
                .andExpect(status().isUnauthorized());
    }
}
