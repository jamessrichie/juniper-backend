package types;

public class AuthTokens {
    public String accessToken;
    public String refreshToken;

    public AuthTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
