package controller.user;

import java.io.*;
import java.sql.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.*;
import static helpers.Utilities.*;

@RestController
@RequestMapping("/v1.0/user")
public class UserController {

    // Token authentication service
    private final AuthTokenService authTokenService;

    // Database connection
    private final DatabaseConnection dbconn;

    // Mailing service
    private final MailService mailService;

    /**
     * Initializes controller
     */
    public UserController() throws IOException {
        authTokenService = new AuthTokenService();
        dbconn = DatabaseConnectionPool.getConnection();
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
        String name = payload.get("name");
        String email = payload.get("email").toLowerCase();
        String password = payload.get("password");

        // check that the email is not in use
        ResponseEntity<String> resolveEmailToUserIdStatus = dbconn.transaction_resolveEmailToUserId(email);
        if (resolveEmailToUserIdStatus.getStatusCode() == HttpStatus.OK) {
            return createStatusJSON("Email is already in use", HttpStatus.BAD_REQUEST);
        }

        String userId = UUID.randomUUID().toString();
        String userHandle = generateUserHandle(name);
        String verificationCode = generateBase62String(64);

        // creates the user
        ResponseEntity<Boolean> createUserStatus = dbconn.transaction_createUser(userId, userHandle, name, email, password, verificationCode);
        if (createUserStatus.getStatusCode() != HttpStatus.OK) {
            return createStatusJSON("Failed to create user", createUserStatus.getStatusCode());
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
            return createStatusJSON(resolveEmailToUserNameStatus);
        }
        String name = resolveEmailToUserNameStatus.getBody();

        // gets the verification code for the user
        ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode(email);
        if (resolveEmailToVerificationCodeStatus.getStatusCode() != HttpStatus.OK) {
            return createStatusJSON("Failed to send email", resolveEmailToVerificationCodeStatus.getStatusCode());
        }
        String verificationCode = resolveEmailToVerificationCodeStatus.getBody();

        // on success, send verification email
        return mailService.sendVerificationEmail(name, email, verificationCode);
    }

    /**
     * Verifies the user's email and redirects them to a status page
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
                    loadTemplate("verification/verification_success_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.OK);

            case BAD_REQUEST -> new ResponseEntity<>(
                    loadTemplate("verification/already_verified_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.BAD_REQUEST);

            case GONE -> new ResponseEntity<>(
                    loadTemplate("verification/verification_expired_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    HttpStatus.GONE);

            default -> new ResponseEntity<>(
                    loadTemplate("verification/verification_failed_page.html").replace("[[year]]",
                                           String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                    processVerificationCodeStatus.getStatusCode());
        };
    }

    /**
     * Updates the user's personal information
     *
     * @param payload JSON object containing "userId", "accessToken", "userHandle", "name", "email", "dateOfBirth" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-personal-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updatePersonalInformation(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String accessToken = payload.get("accessToken");

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String userHandle = payload.get("userHandle");
        String name = payload.get("name");
        String email = payload.get("email").toLowerCase();
        String dateOfBirth = payload.get("dateOfBirth");

        return createStatusJSON(dbconn.transaction_updatePersonalInformation(userId, userHandle, name,
                                                                                       email, dateOfBirth));
    }

    /**
     * Updates the user's education information
     *
     * @param payload JSON object containing "userId", "accessToken", "universityId", "major", "standing", "gpa" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-education-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateEducationInformation(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String accessToken = payload.get("accessToken");

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String universityId = payload.get("universityId");
        String major = payload.get("major");
        String standing = payload.get("standing");
        String gpa = payload.get("gpa");

        return createStatusJSON(dbconn.transaction_updateEducationInformation(userId, universityId, major, standing, gpa));
    }

    /**
     * Updates the user's course registration information
     *
     * @param payload JSON object containing "userId", "accessToken", "universityId", "courses[]" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-registration-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateRegistrationInformation(@RequestBody Map<String, Object> payload) {

        String userId = payload.get("userId").toString();
        String accessToken = payload.get("accessToken").toString();

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String universityId = payload.get("universityId").toString();
        List<String> courses = (List<String>) payload.get("courses");

        return createStatusJSON(dbconn.transaction_updateRegistrationInformation(userId, universityId, courses));
    }

    /**
     * Updates the user's biography
     *
     * @param payload JSON object containing "userId", "accessToken", "biography" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-biography",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateBiography(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String accessToken = payload.get("accessToken");

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String biography = payload.get("biography");

        return createStatusJSON(dbconn.transaction_updateBiography(userId, biography));
    }

    /**
     * Updates the user's card color
     *
     * @param payload JSON object containing "userId", "accessToken", "cardColor" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-card-color",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateCardColor(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String accessToken = payload.get("accessToken");

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String cardColor = payload.get("cardColor");

        return createStatusJSON(dbconn.transaction_updateCardColor(userId, cardColor));
    }

    /**
     * Updates the user's media
     *
     * @param payload JSON object containing "userId", "accessToken", "mediaUrls[]" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-media",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateMedia(@RequestBody Map<String, Object> payload) {

        String userId = payload.get("userId").toString();
        String accessToken = payload.get("accessToken").toString();

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        List<String> mediaUrls = (List<String>) payload.get("mediaUrls");

        return createStatusJSON(dbconn.transaction_updateMedia(userId, mediaUrls));
    }

    /**
     * Updates the user's profile picture
     *
     * @param payload JSON object containing "userId", "accessToken", "profilePictureUrl" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-profile-pic",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateProfilePicture(@RequestBody Map<String, String> payload) {

        String userId = payload.get("userId");
        String accessToken = payload.get("accessToken");

        // verifies access token
        if (!authTokenService.verifyAccessToken(userId, accessToken)) {
            return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
        }

        String profilePictureUrl = payload.get("profilePictureUrl");

        return createStatusJSON(dbconn.transaction_updateProfilePicture(userId, profilePictureUrl));
    }

    /**
     * Generates a unique user handle from a name
     */
    private String generateUserHandle(String name) {
        String userHandle = name.replaceAll("\\s", "") + "#" + String.format("%04d", new Random().nextInt(10000));

        // check that user handle is unique
        while (dbconn.transaction_userHandleToUserId(userHandle).getBody() != null) {
            userHandle = name.replaceAll("\\s", "").toLowerCase() + "#" + String.format("%04d", new Random().nextInt(10000));
        }
        return userHandle;
    }

    /**
     * Generates a random base62 string of specified length
     */
    private String generateBase62String(int length) {
        int leftLimit = 48;
        int rightLimit = 122;

        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
          .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
          .limit(length)
          .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
          .toString();
    }
}
