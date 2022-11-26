package services;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.time.*;
import java.time.temporal.*;

import com.auth0.jwt.*;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.http.*;
import org.springframework.util.ResourceUtils;

import model.DatabaseConnection;

/**
 * Consult section 1.5 of the following standard for further details <br>
 * <a href="https://www.rfc-editor.org/rfc/rfc6749#section-1.5">The OAuth 2.0 Authorization Framework</a>
 *
 * Current implementation of the AuthTokenService does not allow for multiple clients to log into
 * the same account. Logging into a new client will generate a new token family and invalidate
 * previous refresh tokens. We will pretend that this is a feature, not a bug, à la Snapchat
 */
public class AuthTokenService {

    // Database connection
    private final DatabaseConnection dbconn;

    private final String apiHost;

    private final Algorithm HMAC256Algorithm;
    private final JWTVerifier accessTokenVerifier;
    private final JWTVerifier refreshTokenVerifier;

    public AuthTokenService() throws IOException, SQLException {
        this(new DatabaseConnection());
    }

    public AuthTokenService(DatabaseConnection dbconn) throws IOException, SQLException {
        this.dbconn = dbconn;

        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/jwt.credentials")));

        apiHost = configProps.getProperty("API_HOST");
        String privateKey = configProps.getProperty("JWT_PRIVATE_KEY");

        HMAC256Algorithm = Algorithm.HMAC256(privateKey);

        accessTokenVerifier = JWT.require(HMAC256Algorithm)
                .withIssuer("auth0")
                .withAudience(apiHost)
                .withClaim("token_type", "access")
                .build();

        refreshTokenVerifier = JWT.require(HMAC256Algorithm)
                .withIssuer("auth0")
                .withAudience(apiHost)
                .withClaim("token_type", "refresh")
                .build();
    }

    /**
     * Generates a refresh token valid for 6 months
     *
     * @return a signed JSON Web Token
     */
    public String generateToken(String issuer, String subject, String audience, Instant issueTime, Instant expirationTime,
                                String tokenId, String tokenFamily, String tokenType, Algorithm algorithm) {
        try {
            return JWT.create()
                    .withIssuer(issuer)
                    .withSubject(subject)
                    .withAudience(audience)
                    .withIssuedAt(issueTime)
                    .withExpiresAt(expirationTime)
                    .withJWTId(tokenId)
                    .withClaim("token_family", tokenFamily)
                    .withClaim("token_type", tokenType)
                    .sign(algorithm);

        } catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates an access token valid for 10 minutes
     *
     * @return a signed JSON Web Token
     */
    public String generateAccessToken(String userId) {
        return generateToken("auth0", userId, apiHost, Instant.now(), Instant.now().plus(10, ChronoUnit.MINUTES),
                             UUID.randomUUID().toString(), null, "access", HMAC256Algorithm);
    }

    /**
     * Generates a refresh token valid for 6 months / 180 days
     *
     * @return a signed JSON Web Token
     */
    public String generateRefreshToken(String userId, String refreshTokenId, String refreshTokenFamily) {
        return generateToken("auth0", userId, apiHost, Instant.now(), Instant.now().plus(180, ChronoUnit.DAYS),
                             refreshTokenId, refreshTokenFamily, "refresh", HMAC256Algorithm);
    }

    /**
     * Simultaneously generates an access and refresh token within a new token family
     *
     * @return comma-separated signed JSON Web Tokens
     */
    public String generateAccessAndRefreshTokens(String userId) {
        return generateAccessAndRefreshTokens(userId, UUID.randomUUID().toString());
    }

    /**
     * Simultaneously generates an access and refresh token within the same token family
     *
     * @return comma-separated signed JSON Web Tokens
     */
    public String generateAccessAndRefreshTokens(String userId, String refreshTokenFamily) {
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = generateAccessToken(userId);
        String refreshToken = generateRefreshToken(userId, refreshTokenId, refreshTokenFamily);

        if (accessToken == null || refreshToken == null) {
            return null;
        }
        ResponseEntity<Boolean> updateRefreshTokenStatus = dbconn.transaction_updateRefreshToken(userId, refreshTokenId, refreshTokenFamily);
        if (updateRefreshTokenStatus.getStatusCode() != HttpStatus.OK) {
            return null;
        }
        return accessToken + "," + refreshToken;
    }

    /**
     * Verifies an access token
     *
     * @return true iff valid
     */
    public Boolean verifyAccessToken(String userId, String token) {
        try {
            DecodedJWT decodedToken = accessTokenVerifier.verify(token);

            assert(decodedToken.getSubject().equals(userId));
            assert(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));
            return true;

        } catch (AssertionError e){
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifies a refresh token
     *
     * @return if valid, then return an AuthTokens object containing a new
     * access and refresh token. otherwise return null
     */
    public String verifyRefreshToken(String userId, String token) {
        try {
            DecodedJWT decodedToken = refreshTokenVerifier.verify(token);

            assert(decodedToken.getSubject().equals(userId));
            assert(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));

            String tokenId = decodedToken.getId();
            String tokenFamily = decodedToken.getClaim("token_family").asString();
            ResponseEntity<Boolean> verifyRefreshTokenIdStatus = dbconn.transaction_verifyRefreshTokenId(userId, tokenId);
            ResponseEntity<Boolean> verifyRefreshTokenFamilyStatus = dbconn.transaction_verifyRefreshTokenFamily(userId, tokenFamily);

            if (Boolean.TRUE.equals(verifyRefreshTokenIdStatus.getBody())) {
                // if refresh token is valid, then generate new access and refresh tokens within the same token family and allow access
                return generateAccessAndRefreshTokens(userId, tokenFamily);

            } else if (Boolean.TRUE.equals(verifyRefreshTokenFamilyStatus.getBody())) {
                // if refresh token is invalid and belongs to current token family, then revoke token family and deny access
                dbconn.transaction_updateRefreshToken(userId, null, null);
                return null;

            } else {
                // if refresh token is invalid but does not belong to current token family, then deny access
                return null;
            }
        } catch (AssertionError e) {
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
