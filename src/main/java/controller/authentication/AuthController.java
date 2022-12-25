package controller.authentication;

import java.io.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.AuthTokenService;
import types.AuthTokens;
import static helpers.Utilities.*;

@RestController
@RequestMapping("/v1.0/auth")
public class AuthController {

    // Token authentication service
    private final AuthTokenService authTokenService;

    // Database connection
    private final DatabaseConnection dbconn;

    /**
     * Initializes controller
     */
    public AuthController() throws IOException {
        authTokenService = new AuthTokenService();
        dbconn = DatabaseConnectionPool.getConnection();
    }

    /**
     * Verifies whether the supplied credentials are valid
     *
     * @param payload JSON object containing "email", "password" fields
     * @apiNote POST request
     *
     * @return JSON object containing access and refresh tokens within new token family if success.
     *         otherwise, JSON object containing status message.
     *         200 status code iff success
     */
    @RequestMapping(path = "/verify-credentials",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> verifyUserCredentials(@RequestBody Map<String, String> payload) {

        String email = payload.get("email").toLowerCase();
        String password = payload.get("password");

        // check that the credentials are correct
        ResponseEntity<Boolean> verifyCredentialsStatus = dbconn.transaction_verifyCredentials(email, password);
        if (Boolean.FALSE.equals(verifyCredentialsStatus.getBody())) {
            return createStatusJSON("Incorrect credentials", HttpStatus.UNAUTHORIZED);
        }

        // check that the email has been verified
        ResponseEntity<Boolean> verifyEmailStatus = dbconn.transaction_verifyEmail(email);
        if (Boolean.FALSE.equals(verifyEmailStatus.getBody())) {
            return createStatusJSON("Please verify your email before logging in", HttpStatus.BAD_REQUEST);
        }

        // get the userId for token generation
        ResponseEntity<String> resolveEmailToUserIdStatus = dbconn.transaction_resolveEmailToUserId(email);
        if (resolveEmailToUserIdStatus.getStatusCode() != HttpStatus.OK) {
            return createStatusJSON("Failed to verify credentials", HttpStatus.BAD_REQUEST);
        }

        String userId = resolveEmailToUserIdStatus.getBody();
        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Updates the user's credentials
     *
     * @param payload JSON object containing "userId", "password", "newPassword" fields
     * @apiNote POST request
     *
     * @return JSON object containing access and refresh tokens within new token family if success.
     *         otherwise, JSON object containing status message.
     *         200 status code iff success
     */
    @RequestMapping(path = "/update-credentials",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateUserCredentials(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String password = payload.get("password");
        String newPassword = payload.get("newPassword");

        ResponseEntity<Boolean> updateCredentialsStatus = dbconn.transaction_updateCredentials(userId, password, newPassword);
        if (Boolean.FALSE.equals(updateCredentialsStatus.getBody())) {
            return createStatusJSON("Incorrect credentials", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Revokes all refresh tokens. All remaining access tokens will expire within 10 minutes
     *
     * @param payload JSON object containing "userId" field
     * @apiNote POST request
     *
     * @return JSON object containing status message. 200 status code iff success
     */
    @RequestMapping(path = "/revoke-tokens",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> revokeTokens(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");

        if (authTokenService.revokeTokens(userId)) {
            return createStatusJSON("All tokens revoked", HttpStatus.OK);
        } else {
            return createStatusJSON("Failed to revoke tokens", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Renews access and refresh tokens given valid refresh token
     *
     * @param payload JSON object containing "userId", "refreshToken" fields
     * @apiNote POST request
     *
     * @return JSON object containing access and refresh tokens within current token family if success.
     *         otherwise, JSON object containing status message.
     *         200 status code iff success
     */
    @RequestMapping(path = "/renew-tokens",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> renewTokens(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String refreshToken = payload.get("refreshToken");
        AuthTokens tokens = authTokenService.verifyRefreshToken(userId, refreshToken);

        if (tokens != null) {
            return new ResponseEntity<>(tokens, HttpStatus.OK);
        } else {
            return createStatusJSON("Refresh token rejected", HttpStatus.UNAUTHORIZED);
        }
    }
}
