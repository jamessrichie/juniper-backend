package services;

import java.io.*;
import java.util.*;
import java.time.*;
import java.time.temporal.*;

import com.auth0.jwt.*;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import model.DatabaseConnectionPool;
import org.springframework.http.*;
import org.springframework.util.ResourceUtils;

import model.*;
import types.AuthTokens;

/**
 * Consult section 1.5 of the following standard for further details <br>
 * <a href="https://www.rfc-editor.org/rfc/rfc6749#section-1.5">The OAuth 2.0 Authorization Framework</a> <br><br>
 *
 * Current implementation of the AuthTokenService does not allow for multiple clients to log into
 * the same account. Logging into a new client will generate a new token family and invalidate
 * previous refresh tokens. We will pretend that this is a feature, not a bug, à la Snapchat
 */
public class AuthTokenService {

    private final String API_HOST;

    private final Algorithm HMAC256Algorithm;
    private final JWTVerifier accessTokenVerifier;
    private final JWTVerifier refreshTokenVerifier;

    /**
     * Creates a new AuthTokenService instance
     */
    public AuthTokenService() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/jwt.credentials")));

        API_HOST = configProps.getProperty("API_HOST");
        String privateKey = configProps.getProperty("JWT_PRIVATE_KEY");

        HMAC256Algorithm = Algorithm.HMAC256(privateKey);

        accessTokenVerifier = JWT.require(HMAC256Algorithm)
                .withIssuer("auth0")
                .withAudience(API_HOST)
                .withClaim("token_type", "access")
                .build();

        refreshTokenVerifier = JWT.require(HMAC256Algorithm)
                .withIssuer("auth0")
                .withAudience(API_HOST)
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
        return generateToken("auth0", userId, API_HOST, Instant.now(), Instant.now().plus(10, ChronoUnit.MINUTES),
                             UUID.randomUUID().toString(), null, "access", HMAC256Algorithm);
    }

    /**
     * Generates a refresh token valid for 6 months / 180 days
     *
     * @return a signed JSON Web Token
     */
    public String generateRefreshToken(String userId, String refreshTokenId, String refreshTokenFamily) {
        return generateToken("auth0", userId, API_HOST, Instant.now(), Instant.now().plus(180, ChronoUnit.DAYS),
                             refreshTokenId, refreshTokenFamily, "refresh", HMAC256Algorithm);
    }

    /**
     * Generates an access and refresh token within a new token family
     *
     * @return comma-separated signed JSON Web Tokens
     */
    public AuthTokens generateAccessAndRefreshTokens(String userId) {
        return generateAccessAndRefreshTokens(userId, UUID.randomUUID().toString());
    }

    /**
     * Generates an access and refresh token within the same token family
     *
     * @return comma-separated signed JSON Web Tokens
     */
    public AuthTokens generateAccessAndRefreshTokens(String userId, String refreshTokenFamily) {
        String refreshTokenId = UUID.randomUUID().toString();

        String accessToken = generateAccessToken(userId);
        String refreshToken = generateRefreshToken(userId, refreshTokenId, refreshTokenFamily);

        if (accessToken == null || refreshToken == null) {
            return null;
        }
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        ResponseEntity<Boolean> updateRefreshTokenStatus = dbconn.transaction_updateRefreshToken(userId, refreshTokenId, refreshTokenFamily);
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        if (updateRefreshTokenStatus.getStatusCode() != HttpStatus.OK) {
            return null;
        }
        return new AuthTokens(accessToken, refreshToken);
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
     * @return if valid, then return a new access and refresh token. otherwise return null
     */
    public AuthTokens verifyRefreshToken(String userId, String token) {
        try {
            DecodedJWT decodedToken = refreshTokenVerifier.verify(token);

            assert(decodedToken.getSubject().equals(userId));
            assert(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));
            assert(decodedToken.getClaim("token_type").asString().equals("refresh"));

            String tokenId = decodedToken.getId();
            String tokenFamily = decodedToken.getClaim("token_family").asString();

            DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<Boolean> verifyRefreshTokenIdStatus = dbconn.transaction_verifyRefreshTokenId(userId, tokenId);
            ResponseEntity<Boolean> verifyRefreshTokenFamilyStatus = dbconn.transaction_verifyRefreshTokenFamily(userId, tokenFamily);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

            if (Boolean.TRUE.equals(verifyRefreshTokenIdStatus.getBody())) {
                // if refresh token is valid, then generate new access and refresh tokens within the same token family and allow access
                return generateAccessAndRefreshTokens(userId, tokenFamily);

            } else if (Boolean.TRUE.equals(verifyRefreshTokenFamilyStatus.getBody())) {
                // if refresh token is invalid and belongs to current token family, then revoke token family and deny access
                dbconn = DatabaseConnectionPool.getConnection();
                dbconn.transaction_updateRefreshToken(userId, null, null);
                dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

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

    /**
     * Revokes all refresh tokens. All remaining access tokens will expire within 10 minutes
     */
    public Boolean revokeTokens(String userId) {
        DatabaseConnection dbconn = null;
        try {
            dbconn = DatabaseConnectionPool.getConnection();
            return dbconn.transaction_updateRefreshToken(userId, null, null).getBody();

        } finally {
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }
}
