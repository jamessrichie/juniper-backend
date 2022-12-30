package controller.user;

import java.io.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.*;
import static helpers.Utilities.*;

@RestController
@RequestMapping("/user")
public class UserController {

    // Token authentication service
    private final AuthTokenService authTokenService;

    // Mailing service
    private final MailService mailService;

    /**
     * Initializes controller
     */
    public UserController() throws IOException {
        authTokenService = new AuthTokenService();
        mailService = new MailService();
    }

    /**
     * Creates a new user account
     * <ul>
     *     <li> If email does not exist, then create user account </li>
     *     <li> If email exists and user is verified, then send password reset code </li>
     *     <li> If email exists and user is unverified, then delete old user account and create user account </li>
     * </ul>
     *
     * @param payload JSON object containing "name", "email", "password" fields
     * @apiNote POST request
     *
     * @return JSON object containing status message. 200 status code iff success or user already exists
     */
    @RequestMapping(path = "/create",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> createUser(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String name = payload.get("name");
            String email = payload.get("email").toLowerCase();
            String password = payload.get("password");

            // Checks whether the user is verified
            ResponseEntity<Boolean> checkEmailVerifiedStatus = dbconn.transaction_checkEmailVerified(email);

            if (checkEmailVerifiedStatus.getStatusCode() == HttpStatus.OK) {
                // If email exists...
                if (Boolean.TRUE.equals(checkEmailVerifiedStatus.getBody())) {
                    // If user is verified, then send password reset code
                    ResponseEntity<String> resolveEmailToPasswordResetCodeStatus = dbconn.transaction_generatePasswordResetCode(email);
                    if (resolveEmailToPasswordResetCodeStatus.getStatusCode() != HttpStatus.OK) {
                        // If could not generate a password reset code for the user, then return internal server error
                        return createStatusJSON("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    String passwordResetCode = resolveEmailToPasswordResetCodeStatus.getBody();

                    // Get the name of the registered user
                    ResponseEntity<String> resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName(email);
                    if (resolveEmailToUserNameStatus.getStatusCode() != HttpStatus.OK) {
                        // If could not find a name for the user, then return internal server error
                        return createStatusJSON("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    name = resolveEmailToUserNameStatus.getBody();

                    // On success, send verification email
                    return mailService.sendPasswordResetEmail(name, email, passwordResetCode);

                } else {
                    // If user is unverified, then delete old user account and create user account
                    ResponseEntity<Boolean> deleteUnverifiedUserStatus = dbconn.transaction_deleteUnverifiedUser(email);
                    if (deleteUnverifiedUserStatus.getStatusCode() != HttpStatus.OK) {
                        // If could not delete user, then return internal server error
                        return createStatusJSON("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                }
            }
            // Creates user account
            String userHandle = generateUserHandle(dbconn, name);
            String verificationCode = generateSecureString(64);

            ResponseEntity<Boolean> createUserStatus = dbconn.transaction_createUser(userHandle, name, email, password, verificationCode);
            if (createUserStatus.getStatusCode() != HttpStatus.OK) {
                return createStatusJSON("Failed to create user", createUserStatus.getStatusCode());
            }
            // On success, send verification email
            return mailService.sendVerificationEmail(name, email, verificationCode);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Sends an account verification email.
     * <ul>
     *     <li> If email does not exist, then return email sent message </li>
     *     <li> If email exists and verification code is active, then send verification email </li>
     *     <li> If email exists, verification code has expired, and user is verified, then send verification email </li>
     *     <li> If email exists, verification code has expired, and user is unverified, then delete old user account and
     *          return verification code expired message </li>
     * </ul>
     *
     * @param payload JSON object containing "email" field
     * @apiNote POST request
     *
     * @return JSON object containing status message. 200 status code iff success or user does not exist
     */
    @RequestMapping(path = "/request-account-verification",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> sendAccountVerificationEmail(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String email = payload.get("email").toLowerCase();

            // Gets the verification code for the user and identifies whether the verification code is active
            ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode(email);
            String verificationCode = resolveEmailToVerificationCodeStatus.getBody();

            // Checks whether the user is verified
            ResponseEntity<Boolean> checkEmailVerifiedStatus = dbconn.transaction_checkEmailVerified(email);

            if (resolveEmailToVerificationCodeStatus.getStatusCode() == HttpStatus.OK ||
                resolveEmailToVerificationCodeStatus.getStatusCode() == HttpStatus.BAD_REQUEST && Boolean.TRUE.equals(checkEmailVerifiedStatus.getBody())) {
                // If (email exists and verification code is active) or (email exists, verification code has expired, and user is verified),
                // then send verification email
                ResponseEntity<String> resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName(email);
                if (resolveEmailToUserNameStatus.getStatusCode() != HttpStatus.OK) {
                    // If could not find a name for the user, then return internal server error
                    return createStatusJSON("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                String name = resolveEmailToUserNameStatus.getBody();

                return mailService.sendVerificationEmail(name, email, verificationCode);

            } else if (resolveEmailToVerificationCodeStatus.getStatusCode() == HttpStatus.BAD_REQUEST && Boolean.FALSE.equals(checkEmailVerifiedStatus.getBody())) {
                // If email exists, verification code has expired, and user is unverified, then delete old user account
                dbconn.transaction_deleteUnverifiedUser(email);
            }
            // If email does not exist, then return email sent message
            return createStatusJSON("Successfully sent email", HttpStatus.OK);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Verifies the user's email and redirects them to a status page
     * <ul>
     *     <li> If email does not exist, then return verification code expired page </li>
     *     <li> If email exists and user is verified, then return account already verified page </li>
     *     <li> If email exists, user is not verified, and verification code has expired, then return verification code expired page </li>
     *     <li> If email exists, user is not verified, and verification code is active, then return verification success page </li>
     * </ul>
     *
     * @apiNote GET request
     * @return HTML page. 200 status code iff success
     */
    @RequestMapping(path = "/verify-account",
        method = RequestMethod.GET)
    public ResponseEntity<String> verifyAccount(@RequestParam(value = "code") String verificationCode) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            ResponseEntity<Boolean> processVerificationCodeStatus = dbconn.transaction_processAccountVerificationCode(verificationCode);

            return switch (processVerificationCodeStatus.getStatusCode()) {
                case OK -> new ResponseEntity<>(
                        loadTemplate("verify_account_success_page.html")
                                .replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                        HttpStatus.OK);

                case BAD_REQUEST -> new ResponseEntity<>(
                        loadTemplate("verify_account_already_page.html")
                                .replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                        HttpStatus.BAD_REQUEST);

                case NOT_FOUND -> new ResponseEntity<>(
                        loadTemplate("verify_account_expired_page.html")
                                .replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                        HttpStatus.NOT_FOUND);

                default -> new ResponseEntity<>(
                        loadTemplate("verify_account_failed_page.html")
                                .replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR))),
                        processVerificationCodeStatus.getStatusCode());
            };
        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Updates the user's personal information
     *
     * @param payload JSON object containing "userId", "accessToken", "userHandle", "name", "email", "dateOfBirth" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     * TODO: if email is being changed, send an account verification email to the new address. if account is verified, then change email
     */
    @RequestMapping(path = "/update-personal-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updatePersonalInformation(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String userHandle = payload.get("userHandle");
            String name = payload.get("name");
            String email = payload.get("email").toLowerCase();
            String dateOfBirth = payload.get("dateOfBirth");

            return createStatusJSON(dbconn.transaction_updatePersonalInformation(userId, userHandle, name, email, dateOfBirth));
        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Updates the user's education information
     *
     * @param payload JSON object containing "userId", "accessToken", "universityName", "major", "standing", "gpa" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-education-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateEducationInformation(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String universityName = payload.get("universityName");
            String major = payload.get("major");
            String standing = payload.get("standing");
            String gpa = payload.get("gpa");

            return createStatusJSON(dbconn.transaction_updateEducationInformation(userId, universityName, major, standing, gpa));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Updates the user's course registration information
     *
     * @param payload JSON object containing "userId", "accessToken", "universityName", "courseCodes[]" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/update-registration-info",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> updateRegistrationInformation(@RequestBody Map<String, Object> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId").toString();
            String accessToken = payload.get("accessToken").toString();

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String universityName = payload.get("universityName").toString();
            List<String> courseCodes = (List<String>) payload.get("courseCodes");

            return createStatusJSON(dbconn.transaction_updateRegistrationInformation(userId, universityName, courseCodes));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
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

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String biography = payload.get("biography");

            return createStatusJSON(dbconn.transaction_updateBiography(userId, biography));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
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

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String cardColor = payload.get("cardColor");

            return createStatusJSON(dbconn.transaction_updateCardColor(userId, cardColor));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
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

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId").toString();
            String accessToken = payload.get("accessToken").toString();

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            List<String> mediaUrls = (List<String>) payload.get("mediaUrls");

            return createStatusJSON(dbconn.transaction_updateMedia(userId, mediaUrls));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
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

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String profilePictureUrl = payload.get("profilePictureUrl");

            return createStatusJSON(dbconn.transaction_updateProfilePicture(userId, profilePictureUrl));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * Generates a unique user handle from a name
     */
    private String generateUserHandle(DatabaseConnection dbconn, String name) {
        String userHandle = name.replaceAll("\\s", "") + "#" + String.format("%04d", new Random().nextInt(10000));

        // Check that user handle is unique
        while (dbconn.transaction_resolveUserHandleToUserId(userHandle).getBody() != null) {
            userHandle = name.replaceAll("\\s", "").toLowerCase() + "#" + String.format("%04d", new Random().nextInt(10000));
        }
        return userHandle;
    }
}
