package controller;

import java.io.*;
import java.util.*;

import com.google.gson.*;
import org.junit.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import types.*;
import model.database.*;

import static helpers.Utilities.*;
import static org.junit.Assert.*;

public final class UserControllerTest extends ControllerTest {

    @Test
    public void testCreateUser() {
        DatabaseConnection dbconn = null;

        try {
            // Create user1
            Map<String, Object> body = generateBody("name", "name1",
                                                    "email", "name1@email.com",
                                                    "password", "password1");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/create", body);

            // Check that request to create user1 is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

            // Check that user1 is created in database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName("name1@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals("name1", resolveEmailToUserNameStatus.getBody());

            // Create user2 with duplicate email
            body = generateBody("name", "name2",
                                "email", "name1@email.com",
                                "password", "password2");
            postResponse = sendPostRequest("/user/create", body);

            // Check that request to create user2 is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

            // Check that user1 has been replaced by user2 in database
            dbconn = DatabaseConnectionPool.getConnection();
            resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName("name1@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals("name2", resolveEmailToUserNameStatus.getBody());

            // Check that password reset code has not been generated
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToPasswordResetCode = dbconn.transaction_resolveEmailToPasswordResetCode("name1@email.com");
            assertEquals(null, resolveEmailToPasswordResetCode.getBody());
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

            verifyUser("name1@email.com");

            // Create user3 with duplicate email
            body = generateBody("name", "name3",
                                "email", "name1@email.com",
                                "password", "password3");
            postResponse = sendPostRequest("/user/create", body);

            // Check that request to create user3 is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

            // Check that user2 has not been changed in database
            dbconn = DatabaseConnectionPool.getConnection();
            resolveEmailToUserNameStatus = dbconn.transaction_resolveEmailToUserName("name1@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals("name2", resolveEmailToUserNameStatus.getBody());

            // Check that password reset code has been generated
            dbconn = DatabaseConnectionPool.getConnection();
            resolveEmailToPasswordResetCode = dbconn.transaction_resolveEmailToPasswordResetCode("name1@email.com");
            assertNotNull(resolveEmailToPasswordResetCode.getBody());
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testSendAccountVerificationEmail() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");

            // Get the verification code for the user
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode("name@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, resolveEmailToVerificationCodeStatus.getStatusCode());
            String verificationCode = resolveEmailToVerificationCodeStatus.getBody();

            // Request account verification email
            Map<String, Object> body = generateBody("email", "name@email.com");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/request-account-verification", body);

            // Check that request for account verification email is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

            // Verify account
            Map<String, String> parameters = generateParameters("code", verificationCode);
            ResponseEntity<String> getResponse = sendGetRequest("/user/verify-account", parameters);

            // Check that request to verify account is successful
            assertEquals(HttpStatus.OK, getResponse.getStatusCode());

            // Check that account is verified in database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<Boolean> checkEmailVerifiedStatus = dbconn.transaction_checkEmailVerified("name@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(Boolean.TRUE, checkEmailVerifiedStatus.getBody());

            // Request account verification email
            postResponse = sendPostRequest("/user/request-account-verification", body);

            // Check that request for account verification email is successful even though account is already verified
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

            // Request account verification email for non-existent user
            body = generateBody("email", "name1@email.com");
            postResponse = sendPostRequest("/user/request-account-verification", body);

            // Check that request for account verification is successful even though account does not exist
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());
            assertEquals("Successfully sent email", extractStringFromJsonObject(postResponse.getBody(), "status"));

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testVerifyAccount() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");

            // Get the verification code for the user
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToVerificationCodeStatus = dbconn.transaction_resolveEmailToVerificationCode("name@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, resolveEmailToVerificationCodeStatus.getStatusCode());
            String verificationCode = resolveEmailToVerificationCodeStatus.getBody();

            // Verify account
            Map<String, String> parameters = generateParameters("code", verificationCode);
            ResponseEntity<String> getResponse = sendGetRequest("/user/verify-account", parameters);

            // Check that request to verify account is successful
            assertEquals(HttpStatus.OK, getResponse.getStatusCode());

            // Check that account is verified in database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<Boolean> checkEmailVerifiedStatus = dbconn.transaction_checkEmailVerified("name@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(Boolean.TRUE, checkEmailVerifiedStatus.getBody());

            // Check appropriate response for request to verify a verified account
            getResponse = sendGetRequest("/user/verify-account", parameters);
            assertEquals(HttpStatus.BAD_REQUEST, getResponse.getStatusCode());

            // Verify non-existent account
            parameters = generateParameters("code", "code");
            getResponse = sendGetRequest("/user/verify-account", parameters);

            // Check appropriate response for request to verify nonexistent account
            assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatusCode());

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdatePersonalInformation() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            // Update personal info with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "userHandle", "userHandle",
                                                    "name", "name1",
                                                    "email", "name1@email.com",
                                                    "dateOfBirth", "2000-01-01");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-personal-info", body);

            // Check that request to update personal info fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update personal info with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "userHandle", "userHandle",
                                "name", "name1",
                                "email", "name1@email.com",
                                "dateOfBirth", "2000-01-01");
            postResponse = sendPostRequest("/user/update-personal-info", body);

            // Check that request to update personal info is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update personal info
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "userHandle", "userHandle",
                                "name", "name1",
                                "email", "name1@email.com",
                                "dateOfBirth", "2000-01-01");
            postResponse = sendPostRequest("/user/update-personal-info", body);

            // Check that request to update personal info is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that personal info is updated in database
            assertEquals("userHandle", user.userHandle);
            assertEquals("name1", user.userName);
            assertEquals("2000-01-01", user.dateOfBirth);

            // Check that email is updated in database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<String> resolveEmailToUserIdStatus = dbconn.transaction_resolveEmailToUserId("name1@email.com");
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, resolveEmailToUserIdStatus.getStatusCode());
            assertEquals(userId, resolveEmailToUserIdStatus.getBody());

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateEducationInformation() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            // Update education info with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "universityName", "universityName",
                                                    "major", "major",
                                                    "standing", "standing",
                                                    "gpa", "gpa");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-education-info", body);

            // Check that request to update education info fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update education info with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "universityName", "universityName",
                                "major", "major",
                                "standing", "standing",
                                "gpa", "gpa");
            postResponse = sendPostRequest("/user/update-education-info", body);

            // Check that request to update education info is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update education info
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "universityName", "universityName",
                                "major", "major",
                                "standing", "standing",
                                "gpa", "gpa");
            postResponse = sendPostRequest("/user/update-education-info", body);

            // Check that request to update education info is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that education info is updated in database
            assertEquals("universityName", user.universityName);
            assertEquals("major", user.major);
            assertEquals("standing", user.standing);
            assertEquals("gpa", user.gpa);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateRegistrationInformationCourses() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            List<String> courseCodes = new ArrayList<>();
            courseCodes.add("course1");
            courseCodes.add("course2");
            courseCodes.add("course3");

            // Create university by updating education info
            Map<String, Object> body = generateBody("userId", userId,
                                                    "accessToken", accessToken,
                                                    "universityName", "universityName",
                                                    "major", "major",
                                                    "standing", "standing",
                                                    "gpa", "gpa");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-education-info", body);

            // Check that request to update education info is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Update registration info with wrong user id
            body = generateBody("userId", "userId",
                                "accessToken", accessToken,
                                "universityName", "universityName",
                                "courseCodes", courseCodes);
            postResponse = sendPostRequest("/user/update-registration-info", body);

            // Check that request to update registration info fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update registration info with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "universityName", "universityName",
                                "courseCodes", courseCodes);
            postResponse = sendPostRequest("/user/update-registration-info", body);

            // Check that request to update registration info is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update registration info
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "universityName", "universityName",
                                "courseCodes", courseCodes);
            postResponse = sendPostRequest("/user/update-registration-info", body);

            // Check that request to update registration info is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that registration info is updated in database
            assertEquals(courseCodes, user.courseCodes);

            courseCodes.clear();
            courseCodes.add("course4");
            courseCodes.add("course5");
            courseCodes.add("course6");

            // Update registration info again
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "universityName", "universityName",
                                "courseCodes", courseCodes);
            postResponse = sendPostRequest("/user/update-registration-info", body);

            // Check that request to update registration info is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that previous registration info has been discarded
            assertEquals(courseCodes, user.courseCodes);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateBiography() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            // Update biography with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "biography", "biography");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-biography", body);

            // Check that request to update biography fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update biography with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "biography", "biography");
            postResponse = sendPostRequest("/user/update-biography", body);

            // Check that request to update biography is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update biography
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "biography", "biography");
            postResponse = sendPostRequest("/user/update-biography", body);

            // Check that request to update biography is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that biography is updated in database
            assertEquals("biography", user.biography);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateCardColor() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            // Update card color with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "cardColor", "#000000-#000000");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-card-color", body);

            // Check that request to update card color fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update card color with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "cardColor", "#000000-#000000");
            postResponse = sendPostRequest("/user/update-card-color", body);

            // Check that request to update card color is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update card color
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "cardColor", "#000000-#000000");
            postResponse = sendPostRequest("/user/update-card-color", body);

            // Check that request to update card color is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that card color is updated in database
            assertEquals("#000000-#000000", user.cardColor);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateMedia() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            List<String> mediaUrls = new ArrayList<>();
            mediaUrls.add("url1");
            mediaUrls.add("url2");
            mediaUrls.add("url3");

            // Update media with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "mediaUrls", mediaUrls);
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-media", body);

            // Check that request to update media fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update media with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "mediaUrls", mediaUrls);
            postResponse = sendPostRequest("/user/update-media", body);

            // Check that request to update media is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update media
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "mediaUrls", mediaUrls);
            postResponse = sendPostRequest("/user/update-media", body);

            // Check that request to update media is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that media is updated in database
            assertEquals(mediaUrls, user.mediaUrls);

            mediaUrls.clear();
            mediaUrls.add("url4");
            mediaUrls.add("url5");
            mediaUrls.add("url6");

            // Update media again
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "mediaUrls", mediaUrls);
            postResponse = sendPostRequest("/user/update-media", body);

            // Check that request to update media is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that previous media has been discarded
            assertEquals(mediaUrls, user.mediaUrls);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    @Test
    public void testUpdateProfilePicture() {
        DatabaseConnection dbconn = null;

        try {
            createUser("name", "name@email.com", "password");
            verifyUser("name@email.com");

            JsonObject authTokens = loginUser("name@email.com", "password");
            String accessToken = extractStringFromJsonObject(authTokens, "accessToken");
            String userId = getUserId("name@email.com");

            // Update profile picture with wrong user id
            Map<String, Object> body = generateBody("userId", "userId",
                                                    "accessToken", accessToken,
                                                    "profilePictureUrl", "url");
            ResponseEntity<JsonObject> postResponse = sendPostRequest("/user/update-profile-pic", body);

            // Check that request to update profile picture fails gracefully
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update card color with wrong access token
            body = generateBody("userId", userId,
                                "accessToken", "invalidAccessToken",
                                "profilePictureUrl", "url");
            postResponse = sendPostRequest("/user/update-profile-pic", body);

            // Check that request to update profile picture is denied
            assertEquals(HttpStatus.UNAUTHORIZED, postResponse.getStatusCode());

            // Update card color
            body = generateBody("userId", userId,
                                "accessToken", accessToken,
                                "profilePictureUrl", "url");
            postResponse = sendPostRequest("/user/update-profile-pic", body);

            // Check that request to update profile picture is successful
            assertEquals(HttpStatus.OK, postResponse.getStatusCode());

            // Get user profile from database
            dbconn = DatabaseConnectionPool.getConnection();
            ResponseEntity<User> getUserStatus = dbconn.transaction_getUser(userId);
            dbconn = DatabaseConnectionPool.releaseConnection(dbconn);
            assertEquals(HttpStatus.OK, getUserStatus.getStatusCode());
            User user = getUserStatus.getBody();
            assertNotNull(user);

            // Check that profile picture is updated in database
            assertEquals("url", user.profilePictureUrl);

        } catch (IOException e) {
            throw new RuntimeException(e);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }
}