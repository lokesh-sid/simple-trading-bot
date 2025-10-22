package tradingbot.security.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tradingbot.security.dto.LoginRequest;
import tradingbot.security.dto.LoginResponse;
import tradingbot.security.dto.RefreshTokenRequest;
import tradingbot.security.dto.RegisterRequest;
import tradingbot.security.service.AuthService;

/**
 * Unit tests for AuthController
 * 
 * Tests authentication endpoints including success and error scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Tests")
class AuthControllerTest {
    
    @Mock
    private AuthService authService;
    
    @InjectMocks
    private AuthController authController;
    
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Test123!@#";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
    private static final String TEST_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...refresh";
    private static final String TEST_SCOPE = "user";
    
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private LoginResponse successResponse;
    
    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
            TEST_USERNAME,
            TEST_EMAIL,
            TEST_PASSWORD,
            "Test User"
        );
        
        loginRequest = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        
        successResponse = new LoginResponse(
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            3600L,
            TEST_USER_ID,
            TEST_USERNAME,
            TEST_SCOPE
        );
    }
    
    // ==================== REGISTER TESTS ====================
    
    @Test
    @DisplayName("Should register user successfully")
    void testRegisterSuccess() {
        // Given
        when(authService.register(any(RegisterRequest.class))).thenReturn(successResponse);
        
        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest);
        
        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getBody().accessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getBody().refreshToken());
        assertEquals(TEST_USERNAME, response.getBody().username());
        assertEquals(TEST_SCOPE, response.getBody().scope());
        assertNotNull(response.getBody().issuedAt());
        assertNull(response.getBody().error());
        assertNull(response.getBody().errorDescription());
        
        verify(authService).register(any(RegisterRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for invalid registration")
    void testRegisterFailure() {
        // Given
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Username already exists"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest);
        
        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("invalid_request", response.getBody().error());
        assertEquals("Username already exists", response.getBody().errorDescription());
        assertNull(response.getBody().accessToken());
        assertNull(response.getBody().refreshToken());
        
        verify(authService).register(any(RegisterRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for registration server error")
    void testRegisterServerError() {
        // Given
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Database connection failed"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.register(registerRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("server_error", response.getBody().error());
        assertEquals("Registration failed. Please try again later.", response.getBody().errorDescription());
        assertNull(response.getBody().accessToken());
        
        verify(authService).register(any(RegisterRequest.class));
    }
    
    // ==================== LOGIN TESTS ====================
    
    @Test
    @DisplayName("Should login user successfully")
    void testLoginSuccess() {
        // Given
        when(authService.login(any(LoginRequest.class))).thenReturn(successResponse);
        
        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getBody().accessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getBody().refreshToken());
        assertEquals("Bearer", response.getBody().tokenType());
        assertEquals(3600L, response.getBody().expiresIn());
        assertNull(response.getBody().error());
        
        verify(authService).login(any(LoginRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for invalid credentials")
    void testLoginFailure() {
        // Given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);
        
        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("invalid_grant", response.getBody().error());
        assertEquals("Invalid username or password", response.getBody().errorDescription());
        assertNull(response.getBody().accessToken());
        assertNull(response.getBody().refreshToken());
        
        verify(authService).login(any(LoginRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for login server error")
    void testLoginServerError() {
        // Given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("Service unavailable"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.login(loginRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("server_error", response.getBody().error());
        assertEquals("Login failed. Please try again later.", response.getBody().errorDescription());
        
        verify(authService).login(any(LoginRequest.class));
    }
    
    // ==================== REFRESH TOKEN TESTS ====================
    
    @Test
    @DisplayName("Should refresh token successfully")
    void testRefreshTokenSuccess() {
        // Given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(TEST_REFRESH_TOKEN);
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(successResponse);
        
        // When
        ResponseEntity<LoginResponse> response = authController.refreshToken(refreshRequest);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getBody().accessToken());
        assertNull(response.getBody().error());
        
        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for invalid refresh token")
    void testRefreshTokenFailure() {
        // Given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("invalid-token");
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid or expired refresh token"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.refreshToken(refreshRequest);
        
        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("invalid_token", response.getBody().error());
        assertEquals("Invalid or expired refresh token", response.getBody().errorDescription());
        assertNull(response.getBody().accessToken());
        
        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }
    
    @Test
    @DisplayName("Should return error response for refresh token server error")
    void testRefreshTokenServerError() {
        // Given
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(TEST_REFRESH_TOKEN);
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new RuntimeException("Token service unavailable"));
        
        // When
        ResponseEntity<LoginResponse> response = authController.refreshToken(refreshRequest);
        
        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("server_error", response.getBody().error());
        assertEquals("Token refresh failed. Please try again later.", response.getBody().errorDescription());
        
        verify(authService).refreshToken(any(RefreshTokenRequest.class));
    }
    
    // ==================== LOGOUT TESTS ====================
    
    @Test
    @DisplayName("Should logout successfully")
    void testLogout() {
        // When
        ResponseEntity<?> response = authController.logout();
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // Note: logout returns a Map, not LoginResponse since it doesn't involve authentication
        verifyNoInteractions(authService);
    }
    
    // ==================== HEALTH CHECK TESTS ====================
    
    @Test
    @DisplayName("Should return health status")
    void testHealth() {
        // When
        ResponseEntity<?> response = authController.health();
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        verifyNoInteractions(authService);
    }
    
    // ==================== LOGIN RESPONSE ERROR FACTORY TESTS ====================
    
    @Test
    @DisplayName("LoginResponse.error() should create proper error response")
    void testLoginResponseErrorFactory() {
        // When
        LoginResponse errorResponse = LoginResponse.error("test_error", "Test error message");
        
        // Then
        assertFalse(errorResponse.isSuccess());
        assertTrue(errorResponse.isError());
        assertEquals("test_error", errorResponse.error());
        assertEquals("Test error message", errorResponse.errorDescription());
        assertNull(errorResponse.accessToken());
        assertNull(errorResponse.refreshToken());
        assertNull(errorResponse.tokenType());
        assertNull(errorResponse.expiresIn());
        assertNull(errorResponse.userId());
        assertNull(errorResponse.username());
        assertNull(errorResponse.scope());
        assertNull(errorResponse.issuedAt());
    }
    
    @Test
    @DisplayName("LoginResponse success constructor should set success flag and scope")
    void testLoginResponseSuccessConstructor() {
        // When
        LoginResponse response = new LoginResponse(
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            3600L,
            TEST_USER_ID,
            TEST_USERNAME,
            TEST_SCOPE
        );
        
        // Then
        assertTrue(response.isSuccess());
        assertFalse(response.isError());
        assertEquals(TEST_ACCESS_TOKEN, response.accessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        assertEquals(TEST_USER_ID, response.userId());
        assertEquals(TEST_USERNAME, response.username());
        assertEquals(TEST_SCOPE, response.scope());
        assertNotNull(response.issuedAt());
        assertNull(response.error());
        assertNull(response.errorDescription());
    }
    
    @Test
    @DisplayName("LoginResponse should support OAuth 2.0 scope field")
    void testLoginResponseScope() {
        // When
        LoginResponse response = new LoginResponse(
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            3600L,
            TEST_USER_ID,
            TEST_USERNAME,
            "user admin"
        );
        
        // Then
        assertEquals("user admin", response.scope());
    }
    
    @Test
    @DisplayName("LoginResponse should include issued_at timestamp")
    void testLoginResponseIssuedAt() {
        // Given
        long beforeTimestamp = java.time.Instant.now().getEpochSecond();
        
        // When
        LoginResponse response = new LoginResponse(
            TEST_ACCESS_TOKEN,
            TEST_REFRESH_TOKEN,
            3600L,
            TEST_USER_ID,
            TEST_USERNAME,
            TEST_SCOPE
        );
        
        // Then
        long afterTimestamp = java.time.Instant.now().getEpochSecond();
        assertNotNull(response.issuedAt());
        assertTrue(response.issuedAt() >= beforeTimestamp);
        assertTrue(response.issuedAt() <= afterTimestamp);
    }
}
