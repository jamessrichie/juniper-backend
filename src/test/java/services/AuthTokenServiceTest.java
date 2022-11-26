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

import model.DatabaseConnection;

public class AuthTokenServiceTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(10);

    private Savepoint savepoint;

    private Algorithm testAlgorithm = Algorithm.HMAC256("samplePrivateKey");
    private JWTVerifier tokenVerifier = JWT.require(testAlgorithm).build();

    private static AuthTokenService authTokenService;
    private static DatabaseConnection dbconn;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException, SQLException {
        dbconn = new DatabaseConnection(true);
        authTokenService = new AuthTokenService(dbconn);
    }

    @AfterClass
    public static void tearDownAfterClass() throws SQLException {
        dbconn.closeConnection();
    }

    @Before
    public void setUpBeforeTest() throws SQLException {
        savepoint = dbconn.createSavepoint();
    }

    @After
    public void tearDownAfterTest() throws SQLException {
        dbconn.revertToSavepoint(savepoint);
    }

    @Test
    public void testGenerateToken() {
        String token = authTokenService.generateToken("issuer", "userId", "audience", Instant.now(), Instant.now().plus(10, ChronoUnit.MINUTES),
                                                      "id", "family", "type", testAlgorithm);
        DecodedJWT decodedToken = tokenVerifier.verify(token);

        // check that token claims are as expected
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

        // test that token creation works
        assertNotNull(accessToken1);
        assertNotNull(accessToken2);
        assertNotNull(accessToken3);

        // test that tokens for the same user are unique
        assertNotEquals(accessToken1, accessToken2);
        // test that tokens for different users are unique
        assertNotEquals(accessToken1, accessToken3);
    }

    @Test
    public void testGenerateRefreshToken() {
        String refreshToken1 = authTokenService.generateRefreshToken("userId1", "id1","family1");
        String refreshToken2 = authTokenService.generateRefreshToken("userId1", "id2","family1");
        String refreshToken3 = authTokenService.generateRefreshToken("userId2", "id3","family1");
        String refreshToken4 = authTokenService.generateRefreshToken("userId2", "id4","family2");

        // test that token creation works
        assertNotNull(refreshToken1);
        assertNotNull(refreshToken2);
        assertNotNull(refreshToken3);

        // test that tokens for the same user within the same token family are unique
        assertNotEquals(refreshToken1, refreshToken2);
        // test that tokens for different users within the same token family are unique
        assertNotEquals(refreshToken1, refreshToken3);
        // test that tokens for the same user within different token families are unique
        assertNotEquals(refreshToken3, refreshToken4);
    }

    @Test
    public void testGenerateAccessAndRefreshToken() {
        ResponseEntity<Boolean> createUserStatus = dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");
        assertEquals(HttpStatus.OK, createUserStatus.getStatusCode());

        String accessAndRefreshTokens = authTokenService.generateAccessAndRefreshTokens("userId");
        assertNotNull(accessAndRefreshTokens);
    }

    @Test
    public void testTokenExpiration() throws InterruptedException {
        // generate a token that is valid for 3 seconds
        String token = authTokenService.generateToken("issuer", "userId", "audience", Instant.now(), Instant.now().plus(3, ChronoUnit.SECONDS),
                                                      "id", "access", "type", testAlgorithm);
        DecodedJWT decodedToken = tokenVerifier.verify(token);

        // test that token is valid immediately after generation
        assertTrue(decodedToken.getExpiresAtAsInstant().isAfter(Instant.now()));

        Thread.sleep(3500);

        // test that token is valid >3 seconds after generation
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
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");

        String accessAndRefreshTokens = authTokenService.generateAccessAndRefreshTokens("userId");
        String accessToken = accessAndRefreshTokens.split(",")[0];
        String refreshToken = accessAndRefreshTokens.split(",")[1];

        Boolean accessTokenValid = authTokenService.verifyAccessToken("userId", accessToken);
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);

        assertTrue(accessTokenValid);
        assertNotNull(accessAndRefreshTokens);
    }

    @Test
    public void testRefreshTokenReuseDetection() {
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");

        // generate access and refresh tokens
        String accessAndRefreshTokens = authTokenService.generateAccessAndRefreshTokens("userId");
        String refreshToken = accessAndRefreshTokens.split(",")[1];

        // use the first refresh token. verification should succeed
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(accessAndRefreshTokens);

        String newRefreshToken = accessAndRefreshTokens.split(",")[1];

        // reuse the first refresh token. verification should fail and the token family should be revoked
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNull(accessAndRefreshTokens);

        // reuse the second refresh token. verification should fail since the token family has been revoked
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", newRefreshToken);
        assertNull(accessAndRefreshTokens);
    }

    @Test
    public void testRefreshTokenReuseDetectionNewTokenFamily() {
        dbconn.transaction_createUser("userId", "userHandle", "userName", "email", "password", "verificationCode");

        // generate access and refresh tokens
        String accessAndRefreshTokens = authTokenService.generateAccessAndRefreshTokens("userId");
        String refreshToken = accessAndRefreshTokens.split(",")[1];

        // use the first refresh token from first family. verification should succeed
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(accessAndRefreshTokens);

        // save the second refresh token from first family
        String newRefreshToken = accessAndRefreshTokens.split(",")[1];

        // reuse the first refresh token from first family. verification should fail and the token family should be revoked
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNull(accessAndRefreshTokens);

        // generate access and refresh tokens from a new token family
        accessAndRefreshTokens = authTokenService.generateAccessAndRefreshTokens("userId");
        assertNotNull(accessAndRefreshTokens);
        refreshToken = accessAndRefreshTokens.split(",")[1];

        // use the new refresh token from second family. verification should succeed
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", refreshToken);
        assertNotNull(accessAndRefreshTokens);

        // use the second refresh token from first family. verification should fail since the token family has been revoked
        accessAndRefreshTokens = authTokenService.verifyRefreshToken("userId", newRefreshToken);
        assertNull(accessAndRefreshTokens);
    }
}
