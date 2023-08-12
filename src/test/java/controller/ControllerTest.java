package controller;

import java.io.*;
import java.sql.*;
import java.util.*;

import com.google.gson.*;
import org.junit.*;
import org.junit.rules.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import model.database.*;

import static helpers.Utilities.*;
import static org.junit.Assert.*;

public class ControllerTest {

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(30);

    private Savepoint savepoint;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        // Do not swap the SpringApplication and DatabaseConnectionPool calls
        // or things will inexplicably break
        RestServiceApplication.run();

        DatabaseConnectionPool.enableTesting();
        DatabaseConnectionPool.reducePoolSize();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        DatabaseConnectionPool.disableTesting();
    }

    @Before
    public void setUpBeforeTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        savepoint = dbconn.createSavepoint();
        DatabaseConnectionPool.releaseConnection(dbconn);
    }

    @After
    public void tearDownAfterTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.revertToSavepoint(savepoint);
        DatabaseConnectionPool.releaseConnection(dbconn);
    }

    protected static Map<String, String> generateParameters(Object... args) {
        return (Map<String, String>) (Map) generateBody(args);
    }

    protected static Map<String, Object> generateBody(Object... args) {
        Map<String, Object> map = new HashMap<>();

        for (int i = 0; i < args.length; i += 2) {
            map.put(args[i].toString(), args[i + 1]);
        }
        return map;
    }

    /**
     * Creates a user with the supplied name, email, password
     */
    protected void createUser(String name, String email, String password) {
        try {
            Map<String, Object> body = generateBody("name", name,
                                                    "email", email,
                                                    "password", password);
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/create", body);
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies the user with the supplied email
     */
    protected void verifyUser(String email) {
        try {
            // Get the verification code for the user
            DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode(email);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            String verificationCode = resolveEmailToVerificationCodeStatus.getBody();
            assertNotNull(verificationCode);

            // Verify account
            Map<String, String> parameters = generateParameters("code", verificationCode);
            ResponseEntity<String> getResponse = sendGetRequest("/user/verify-account", parameters);
            assertEquals(HttpStatus.OK, getResponse.getStatusCode());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Logs into the user with the supplied email
     */
    protected JsonObject loginUser(String email, String password) {
        try {
            // Log into the user
            Map<String, Object> body = generateBody("email", email,
                                                    "password", password);
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/auth/login", body);
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Return access and refresh tokens
            return postResponse.getBody();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the user id for the user with the supplied email
     */
    protected String getUserId(String email) {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        ResponseEntity<String> resolveEmailToUserIdStatus = dbconn.transaction_resolveEmailToUserId(email);
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
        assertEquals(HttpStatus.OK, resolveEmailToUserIdStatus.getStatusCode());

        return resolveEmailToUserIdStatus.getBody();
    }
}