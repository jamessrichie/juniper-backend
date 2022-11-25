package controller.authentication;

import java.io.*;
import java.sql.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import exceptions.*;
import services.AuthTokenService;

@RestController
@RequestMapping("/v0/auth")
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
            return new ResponseEntity<>("Please verify your email before logging in", HttpStatus.BAD_REQUEST);
        }

        ResponseEntity<Boolean> verifyCredentialsStatus = dbconn.transaction_verifyCredentials(email, password);
        if (Boolean.FALSE.equals(verifyCredentialsStatus.getBody())) {
            return new ResponseEntity<>("Incorrect credentials", HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<String> getUserIdStatus = dbconn.transaction_getUserId(email);
        if (getUserIdStatus.getStatusCode() != HttpStatus.OK) {
            return new ResponseEntity<>("Failed to verify credentials", HttpStatus.BAD_REQUEST);
        }
        String userId = getUserIdStatus.getBody();

        return new ResponseEntity<>(authTokenService.generateAccessAndRefreshTokens(userId), HttpStatus.OK);
    }

    /**
     * Updates the login credentials of an existing user account
     *
     * @return
     * @url .../user/updateUserCredentials?userId=value1&password=value2&newPassword=value3
     */
    @PostMapping("/updateUserCredentials")
    public Boolean updateUserCredentials(@RequestParam(value = "email") String email,
                                         @RequestParam(value = "new_email") String newEmail,
                                         @RequestParam(value = "password") String password,
                                         @RequestParam(value = "new_password") String newPassword) {

        throw new NotYetImplementedException();
    }


}
