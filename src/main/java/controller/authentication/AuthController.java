package controller.authentication;

import java.io.*;
import java.sql.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.AuthTokenService;

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
    @PostMapping("/login")
    public ResponseEntity<String> verifyUserCredentials(@RequestParam(value = "email") String email,
                                                        @RequestParam(value = "password") String password) {

        email = email.toLowerCase();

        ResponseEntity<Boolean> checkEmailVerificationStatus = dbconn.transaction_checkEmailVerification(email);
        if (Boolean.FALSE.equals(checkEmailVerificationStatus.getBody())) {
            return new ResponseEntity<>("Please verify your email before logging in\n", HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<Boolean> verifyCredentialsStatus = dbconn.transaction_verifyCredentials(email, password);
        if (Boolean.FALSE.equals(verifyCredentialsStatus.getBody())) {
            return new ResponseEntity<>("Incorrect credentials\n", HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> getUserIdStatus = dbconn.transaction_getUserId(email);
        if (getUserIdStatus.getStatusCode() != HttpStatus.OK) {
            return new ResponseEntity<>("Failed to verify credentials\n", HttpStatus.BAD_REQUEST);
        }
        String userId = getUserIdStatus.getBody();

        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Updates the login credentials of an existing user account
     *
     * @return if success, return access and refresh tokens within a new token family
     */
    @PostMapping("/update-credentials")
    public ResponseEntity<String> updateUserCredentials(@RequestParam(value = "userId") String userId,
                                                        @RequestParam(value = "password") String password,
                                                        @RequestParam(value = "new_email") String newEmail,
                                                        @RequestParam(value = "new_password") String newPassword) {

        newEmail = newEmail.toLowerCase();

        ResponseEntity<Boolean> updateCredentialsStatus = dbconn.transaction_updateCredentials(userId, password, newEmail, newPassword);
        if (Boolean.FALSE.equals(updateCredentialsStatus.getBody())) {
            return new ResponseEntity<>("Incorrect credentials\n", HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Revokes all refresh tokens. All remaining access tokens will expire within 10 minutes
     *
     * @return HTTP status code with message
     */
    @PostMapping("/sign-out")
    public ResponseEntity<String> revokeTokens(@RequestParam(value = "userId") String userId) {

        if (authTokenService.revokeTokens(userId)) {
            return new ResponseEntity<>("All tokens revoked", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Failed to revoke tokens", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Renews access and refresh tokens given valid refresh token
     *
     * @return access and refresh tokens within the same token family
     */
    @PostMapping("/renew-tokens")
    public ResponseEntity<String> renewTokens(@RequestParam(value = "user_id") String userId,
                                              @RequestParam(value = "refresh_token") String refreshToken) {

        String accessAndRefreshTokens = authTokenService.verifyRefreshToken(userId, refreshToken);

        if (accessAndRefreshTokens != null) {
            return new ResponseEntity<>(accessAndRefreshTokens, HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Refresh token rejected\n", HttpStatus.UNAUTHORIZED);
        }
    }
}
