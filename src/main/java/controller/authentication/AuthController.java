package controller.authentication;

import java.io.*;
import java.sql.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import exceptions.*;
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

    @PostMapping("/renew-tokens")
    public ResponseEntity<String> renewTokens(@RequestParam(value = "user_id") String userId,
                                              @RequestParam(value = "refresh_token") String refreshToken) {

        String accessAndRefreshTokens = authTokenService.verifyRefreshToken(userId, refreshToken);

        if (accessAndRefreshTokens != null) {
            return new ResponseEntity<>(accessAndRefreshTokens, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(null, HttpStatus.UNAUTHORIZED);
        }
    }
}
