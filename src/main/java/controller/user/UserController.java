package controller.user;

import java.io.*;
import java.sql.*;
import java.util.*;

import org.apache.commons.text.WordUtils;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import helpers.*;
import services.*;
import exceptions.*;

@RestController
@RequestMapping("/user")
public class UserController {

    // Database connection
    private final DatabaseConnection dbconn;

    // Token authentication service
    private final AuthTokenService authTokenService;

    // Mailing service
    private final MailService mailService;

    /**
     * Initializes controller
     */
    public UserController() throws IOException, SQLException {
        dbconn = new DatabaseConnection();
        authTokenService = new AuthTokenService();
        mailService = new MailService();
    }

    /**
     * Creates a new user account
     *
     * @param payload JSON object containing "name", "email", "password" fields
     * @apiNote POST request
     *
     * @return JSON object containing status message. 200 status code iff success
     */
    @RequestMapping(path = "/create",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> createUser(@RequestBody Map<String, String> payload) {

        String name = WordUtils.capitalizeFully(payload.get("name"));
        String email = payload.get("email").toLowerCase();
        String password = payload.get("password");

        // check that the email is not in use
        ResponseEntity<String> resolveEmailToUserIdStatus = dbconn.transaction_resolveEmailToUserId(email);
        if (resolveEmailToUserIdStatus.getStatusCode() == HttpStatus.OK) {
            return Utilities.createJSONWithStatusMessage("Email is already in use", HttpStatus.BAD_REQUEST);
        }

        String userId = UUID.randomUUID().toString();
        String userHandle = generateUserHandle(name);
        String verificationCode = generateVerificationCode(64);

        // creates the user
        ResponseEntity<Boolean> createUserStatus = dbconn.transaction_createUser(userId, userHandle, name, email, password, verificationCode);
        if (createUserStatus.getStatusCode() != HttpStatus.OK) {
            return Utilities.createJSONWithStatusMessage("Failed to create user", createUserStatus.getStatusCode());
        }
        // on success, send verification email
        return mailService.sendVerificationEmail(name, email, verificationCode);
    }

    /**
     * Sends a verification email
     *
     * @param payload JSON object containing "email" field
     * @apiNote POST request
     *
     * @return JSON object containing status message. 200 status code iff success
     */
    @RequestMapping(path = "/send-verification-email",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> sendVerificationEmail(@RequestBody Map<String, String> payload) {

        String email = payload.get("email").toLowerCase();

        // gets the name of the user
        ResponseEntity<String> resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName(email);
        if (resolveEmailToUserNameStatus.getStatusCode() != HttpStatus.OK) {
            return Utilities.createJSONWithStatusMessage(resolveEmailToUserNameStatus);
        }
        String name = resolveEmailToUserNameStatus.getBody();

        // gets the verification code for the user
        ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode(email);
        if (resolveEmailToVerificationCodeStatus.getStatusCode() != HttpStatus.OK) {
            return Utilities.createJSONWithStatusMessage("Failed to send email", resolveEmailToVerificationCodeStatus.getStatusCode());
        }
        String verificationCode = resolveEmailToVerificationCodeStatus.getBody();

        // on success, send verification email
        return mailService.sendVerificationEmail(name, email, verificationCode);
    }

    /**
     * Verifies a user's email and redirects them to a status page
     *
     * @apiNote GET request
     * @return HTML page. 200 status code iff success
     */
    @RequestMapping(path = "/verify",
        method = RequestMethod.GET)
    public ResponseEntity<String> processVerificationCode(@RequestParam(value = "code") String verificationCode) {

        ResponseEntity<Boolean> processVerificationCodeStatus = dbconn.transaction_processVerificationCode(verificationCode);

        return switch (processVerificationCodeStatus.getStatusCode()) {
            case OK -> new ResponseEntity<>(
                    Utilities.loadTemplate("verification/verification_success_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.OK);

            case BAD_REQUEST -> new ResponseEntity<>(
                    Utilities.loadTemplate("verification/already_verified_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.BAD_REQUEST);

            case GONE -> new ResponseEntity<>(
                    Utilities.loadTemplate("verification/verification_expired_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.GONE);

            default -> new ResponseEntity<>(
                    Utilities.loadTemplate("verification/verification_failed_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    processVerificationCodeStatus.getStatusCode());
        };
    }

    /**
     * Records changes to the user's profile
     *
     * @return true iff operation succeeds
     */
    @RequestMapping(path = "/update-personal-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<String> updatePersonalInformation(@RequestBody Map<String, String> payload) {

        // Overview
        // ––––––––––––––––––––––––––––––––––––––––––––––––––––––
        // assert that userId and password matches those stored
        // in the database before updating the associated profile
        // ––––––––––––––––––––––––––––––––––––––––––––––––––––––

        throw new NotYetImplementedException();
    }

    /**
     * Generates a unique user handle from a name
     */
    private String generateUserHandle(String name) {
        String userHandle = name.replaceAll("\\s", "").toLowerCase() + "#" + String.format("%04d", new Random().nextInt(10000));

        // check that user handle is unique
        while (dbconn.transaction_userHandleToUserId(userHandle).getBody() != null) {
            userHandle = name.replaceAll("\\s", "").toLowerCase() + "#" + String.format("%04d", new Random().nextInt(10000));
        }
        return userHandle;
    }

    /**
     * Generates a random string of specified length
     */
    private String generateVerificationCode(int length) {
        int leftLimit = 97;
        int rightLimit = 122;

        Random random = new Random();
        StringBuilder buffer = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
            buffer.append((char) randomLimitedInt);
        }
        return buffer.toString();
    }
}
