package controller.authentication;

import java.io.*;
import java.sql.*;
import java.util.*;

import helpers.Utilities;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.AuthTokenService;
import types.AuthTokens;

@RestController
@RequestMapping("/auth")
public class AuthController {

    // Database connection
    private final DatabaseConnection dbconn;

    // Token authentication service
    private final AuthTokenService authTokenService;

    public AuthController() throws IOException, SQLException {
        dbconn = new DatabaseConnection();
        authTokenService = new AuthTokenService();
    }

    /**
     * Logs into an existing verified user account
     *
     * @return if success, return access and refresh tokens within a new token family
     */
    @RequestMapping(path = "/verify-credentials",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> verifyUserCredentials(@RequestBody Map<String, String> payload) {

        String email = payload.get("email").toLowerCase();
        String password = payload.get("password");

        // check that the email has been verified
        ResponseEntity<Boolean> checkEmailVerificationStatus = dbconn.transaction_checkEmailVerification(email);
        if (Boolean.FALSE.equals(checkEmailVerificationStatus.getBody())) {
            return Utilities.createJSONResponseEntity("Please verify your email before logging in", HttpStatus.BAD_REQUEST);
        }

        // check that the credentials are correct
        ResponseEntity<Boolean> verifyCredentialsStatus = dbconn.transaction_verifyCredentials(email, password);
        if (Boolean.FALSE.equals(verifyCredentialsStatus.getBody())) {
            return Utilities.createJSONResponseEntity("Incorrect credentials", HttpStatus.UNAUTHORIZED);
        }

        // get the userId for token generation
        ResponseEntity<String> getUserIdStatus = dbconn.transaction_getUserId(email);
        if (getUserIdStatus.getStatusCode() != HttpStatus.OK) {
            return Utilities.createJSONResponseEntity("Failed to verify credentials", HttpStatus.BAD_REQUEST);
        }

        String userId = getUserIdStatus.getBody();
        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Updates the login credentials of an existing user account
     *
     * @return if success, return access and refresh tokens within a new token family
     */
    @RequestMapping(path = "/update-credentials",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateUserCredentials(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String password = payload.get("password");
        String newEmail = payload.get("newEmail").toLowerCase();;
        String newPassword = payload.get("newPassword");

        ResponseEntity<Boolean> updateCredentialsStatus = dbconn.transaction_updateCredentials(userId, password, newEmail, newPassword);
        if (Boolean.FALSE.equals(updateCredentialsStatus.getBody())) {
            return Utilities.createJSONResponseEntity("Incorrect credentials", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Revokes all refresh tokens. All remaining access tokens will expire within 10 minutes
     *
     * @return HTTP status code with message
     */
    @RequestMapping(path = "/revoke-tokens",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> revokeTokens(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");

        if (authTokenService.revokeTokens(userId)) {
            return Utilities.createJSONResponseEntity("All tokens revoked", HttpStatus.OK);
        } else {
            return Utilities.createJSONResponseEntity("Failed to revoke tokens", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Renews access and refresh tokens given valid refresh token
     *
     * @return access and refresh tokens within the same token family
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
            return Utilities.createJSONResponseEntity("Refresh token rejected", HttpStatus.UNAUTHORIZED);
        }
    }
}
