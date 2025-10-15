package tradingbot.security.dto;

/**
 * Response DTO for successful authentication
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn, // seconds
        String userId,
        String username
) {
    public LoginResponse(String accessToken, String refreshToken, long expiresIn, String userId, String username) {
        this(accessToken, refreshToken, "Bearer", expiresIn, userId, username);
    }
}
