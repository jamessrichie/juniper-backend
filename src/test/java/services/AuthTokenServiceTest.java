package services;

import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.*;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.*;
import static org.junit.Assert.*;
import com.auth0.jwt.*;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import model.*;
import types.AuthTokens;

public class AuthTokenServiceTest {

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(10);


    private final Algorithm testAlgorithm = Algorithm.HMAC256("samplePrivateKey");
    private final JWTVerifier tokenVerifier = JWT.require(testAlgorithm).build();

    private Savepoint savepoint;
    private static AuthTokenService authTokenService;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        DatabaseConnectionPool.enableTesting();
        DatabaseConnectionPool.reducePoolSize();
        authTokenService = new AuthTokenService();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        DatabaseConnectionPool.disableTesting();
    }

    @Before
    public void setUpBeforeTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        savepoint = dbconn.createSavepoint();
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
    }

    @After
    public void tearDownAfterTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.revertToSavepoint(savepoint);
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
    }

    @Test
    public void testGenerateToken() {
        String token = authTokenService.generateToken("issuer", "userId", "audience", Instant.now(), Instant.now().plus(10, ChronoUnit.MINUTES),
                                                      "id", "family", "type", testAlgorithm);
        DecodedJWT decodedToken = tokenVerifier.verify(token);

        // Check that token claims are as expected
        assertEquals("issuer", decodedToken.getIssuer());
        assertEquals("userId", decodedToken.getSubject());
        assertEquals("audience", decodedToken.getAudience().get(0));
        assertEquals(10, TimeUnit.MINUTES.convert(decodedToken.getExpiresAt().getTime() - decodedToken.getIssuedAt().getTime(), TimeUnit.MILLISECONDS));
        assertEquals("id", decodedToken.getId());
        assertEquals("family", decodedToken.getClaim("token_family").asString());
        assertEquals("type", decodedToken.getClaim("token_type").asString());
    }

    @Test
    public void testGenerateAccessToken() {
        String accessToken1 = authTokenService.generateAccessToken("userId1");
        String accessToken2 = authTokenService.generateAccessToken("userId1");
        String accessToken3 = authTokenService.generateAccessToken("userId2");

        // Test that token creation works
        assertNotNull(accessToken1);
        assertNotNull(accessToken2);
        assertNotNull(accessToken3);

        // Test that tokens for the same user are unique
        assertNotEquals(accessToken1, accessToken2);
        // Test that tokens for different users are unique
        assertNotEquals(accessToken1, accessToken3);
    }

    @Test
    public void testGenerateRefreshToken() {
        String refreshToken1 = authTokenService.generateRefreshToken("userId1", "id1","family1");
        String refreshToken2 = authTokenService.generateRefreshToken("userId1", "id2","family1");
        String refreshToken3 = authTokenService.generateRefreshToken("userId2", "id3","family1");
        String refreshToken4 = authTokenService.generateRefreshToken("userId2", "id4","family2");

        // Test that token creation works
        assertNotNull(refreshToken1);
        assertNotNull(refreshToken2);
        assertNotNull(refreshToken3);

        // Test that tokens for the same user within the same token family are unique
        assertNotEquals(refreshToken1, refreshToken2);
        // Test that tokens for different users within the same token family are unique
        assertNotEquals(refreshToken1, refreshToken3);
        // Test that tokens for the same user within different token families are unique
        assertNotEquals(refreshToken3, refreshToken4);
    }

    @Test
    public void testGenerateAccessAndRefreshToken() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        ResponseEntity<Boolean> createUserStatus = dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        assertEquals(HttpStatus.OK, createUserStatus.getStatusCode());

        AuthTokens tokens = authTokenService.generateAccessAndRefreshTokens("userId");
        assertNotNull(tokens);
    }

    @Test
    public void testTokenExpiration() throws InterruptedException {
        // Generate a token that is valid for 3 seconds
        String token = authTokenService.generateToken("issuer", "userId", "audience", Instant.now(), Instant.now().plus(3, ChronoUnit.SECONDS),
                                                      "id", "access", "type", testAlgorithm);
        DecodedJWT decodedToken = tokenVerifier.verify(token);

        // Test that token is valid immediately after generation
        assertTrue(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));

        Thread.sleep(3500);

        // Test that token is valid >3 seconds after generation
        assertFalse(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));
    }

    @Test
    public void testVerifyAccessToken() {
        String accessToken = authTokenService.generateAccessToken("userId1");

        assertTrue(authTokenService.verifyAccessToken("userId1", accessToken));
        assertFalse(authTokenService.verifyAccessToken("userId2", accessToken));
    }

    @Test
    public void testVerifyRefreshToken() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        AuthTokens tokens = authTokenService.generateAccessAndRefreshTokens("userId");

        Boolean accessTokenValid = authTokenService.verifyAccessToken("userId", tokens.accessToken);
        tokens = authTokenService.verifyRefreshToken("userId", tokens.refreshToken);

        assertTrue(accessTokenValid);
        assertNotNull(tokens);
    }

    @Test
    public void testRefreshTokenReuseDetection() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        // Generate access and refresh tokens
        AuthTokens tokens = authTokenService.generateAccessAndRefreshTokens("userId");
        String refreshToken = tokens.refreshToken;

        // Use the first refresh token. verification should succeed
        tokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(tokens);

        String newRefreshToken = tokens.refreshToken;

        // Reuse the first refresh token. verification should fail and the token family should be revoked
        tokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNull(tokens);

        // Reuse the second refresh token. verification should fail since the token family has been revoked
        tokens = authTokenService.verifyRefreshToken("userId", newRefreshToken);
        assertNull(tokens);
    }

    @Test
    public void testRefreshTokenReuseDetectionNewTokenFamily() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        // Generate access and refresh tokens
        AuthTokens tokens = authTokenService.generateAccessAndRefreshTokens("userId");
        String refreshToken = tokens.refreshToken;

        // Use the first refresh token from first family. verification should succeed
        tokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(tokens);

        // Save the second refresh token from first family
        String newRefreshToken = tokens.refreshToken;

        // Reuse the first refresh token from first family. verification should fail and the token family should be revoked
        tokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNull(tokens);

        // Generate access and refresh tokens from a new token family
        tokens = authTokenService.generateAccessAndRefreshTokens("userId");
        assertNotNull(tokens);
        refreshToken = tokens.refreshToken;

        // Use the new refresh token from second family. verification should succeed
        tokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(tokens);

        // Use the second refresh token from first family. verification should fail since the token family has been revoked
        tokens = authTokenService.verifyRefreshToken("userId", newRefreshToken);
        assertNull(tokens);
    }
}
