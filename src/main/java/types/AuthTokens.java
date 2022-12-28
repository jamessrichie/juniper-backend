package types;

public class AuthTokens {

    public String accessToken;
    public String refreshToken;

    public AuthTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AuthTokens {\n");
        sb.append("    accessToken: ").append(accessToken).append("\n");
        sb.append("    refreshToken: ").append(refreshToken).append("\n");
        sb.append("}");
        return sb.toString();
    }
}