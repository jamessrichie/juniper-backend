package model;

import java.io.*;
import java.sql.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.time.*;
import java.time.format.*;
import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.springframework.http.*;
import org.springframework.util.ResourceUtils;

import exceptions.*;
import types.*;

import static model.DatabaseStatements.*;
import static helpers.Utilities.*;

public class DatabaseConnection {

    // Database connection
    private final Connection conn;

    // Flag enabling the use of testing features
    private final Boolean testEnabled;

    // Flag enabling the creation of savepoints
    private Boolean testSavepointEnabled;

    // Password hashing parameter constants
    private static final int HASH_STRENGTH = 65536;
    private static final int KEY_LENGTH = 128;

    // Password reset code length
    private static final int RESET_CODE_LENGTH = 64;

    // Number of attempts when encountering deadlock
    private static final int MAX_ATTEMPTS = 16;

    // System statements
    private PreparedStatement systemTransactionCountStatement;

    // Create statements
    private PreparedStatement createCourseStatement;
    private PreparedStatement createMediaStatement;
    private PreparedStatement createRegistrationStatement;
    private PreparedStatement createRelationshipStatement;
    private PreparedStatement createUniversityStatement;
    private PreparedStatement createUserStatement;

    // Delete statements
    private PreparedStatement deleteMediaStatement;
    private PreparedStatement deleteRegistrationStatement;
    private PreparedStatement deleteRelationshipStatement;

    // Update statements
    private PreparedStatement updateBiographyStatement;
    private PreparedStatement updateCardColorStatement;
    private PreparedStatement updateCredentialsStatement;
    private PreparedStatement updateEducationInformationStatement;
    private PreparedStatement updateEmailVerificationStatement;
    private PreparedStatement updatePasswordResetCodeStatement;
    private PreparedStatement updatePersonalInformationStatement;
    private PreparedStatement updateProfilePictureStatement;
    private PreparedStatement updateRefreshTokenStatement;
    private PreparedStatement updateRelationshipStatement;

    // Select statements
    private PreparedStatement resolveCourseCodeUniversityIdToCourseRecordStatement;
    private PreparedStatement resolveEmailToUserRecordStatement;
    private PreparedStatement resolvePasswordResetCodeToUserRecord;
    private PreparedStatement resolveUniversityIdToUniversityRecordStatement;
    private PreparedStatement resolveUniversityNameToUniversityRecordStatement;
    private PreparedStatement resolveUserHandleToUserRecordStatement;
    private PreparedStatement resolveUserIdOtherUserIdToRelationshipRecordStatement;
    private PreparedStatement resolveUserIdToCourseRecordsStatement;
    private PreparedStatement resolveUserIdToMediaRecordsStatement;
    private PreparedStatement resolveUserIdToNumberOfFriendsStatement;
    private PreparedStatement resolveUserIdToRatingStatement;
    private PreparedStatement resolveUserIdToUserRecordStatement;
    private PreparedStatement resolveVerificationCodeToUserRecordStatement;

    /**
     * Creates a connection to the database specified in dbconn.credentials
     */
    public DatabaseConnection() throws IOException, SQLException {
        this(false);
    }

    /**
     * Creates a connection to the database specified in dbconn.credentials
     *
     * @param testEnabled flag enabling the use of testing features
     */
    public DatabaseConnection(Boolean testEnabled) throws IOException, SQLException {
        this.testEnabled = testEnabled;
        conn = openConnectionFromDbConn();
        prepareStatements();
    }

    /**
     * Return the connection specified by the dbconn.credentials file
     */
    private static Connection openConnectionFromDbConn() throws IOException, SQLException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/dbconn.credentials")));

        String endpoint = configProps.getProperty("RDS_ENDPOINT");
        String port = configProps.getProperty("RDS_PORT");
        String dbName = configProps.getProperty("RDS_DB_NAME");
        String username = configProps.getProperty("RDS_USERNAME");
        String password = configProps.getProperty("RDS_PASSWORD");

        String connectionUrl = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;user=%s;password=%s",
                endpoint, port, dbName, username, password);
        Connection conn = DriverManager.getConnection(connectionUrl);

        // Automatically commit after each statement
        conn.setAutoCommit(true);

        // Set the transaction isolation level to serializable
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

        return conn;
    }

    /**
     * Gets the underlying connection
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Closes the application-to-database connection
     */
    public void closeConnection() throws SQLException {
        closeStatements();
        conn.close();
    }

    /*
     * Prepare all the SQL statements
     */
    private void prepareStatements() throws SQLException {
        // System statements
        systemTransactionCountStatement = conn.prepareStatement(SYSTEM_TRANSACTION_COUNT);

        // Create statements
        createCourseStatement = conn.prepareStatement(CREATE_COURSE);
        createMediaStatement = conn.prepareStatement(CREATE_MEDIA);
        createRegistrationStatement = conn.prepareStatement(CREATE_REGISTRATION);
        createRelationshipStatement = conn.prepareStatement(CREATE_RELATIONSHIP);
        createUniversityStatement = conn.prepareStatement(CREATE_UNIVERSITY);
        createUserStatement = conn.prepareStatement(CREATE_USER);

        // Delete statements
        deleteMediaStatement = conn.prepareStatement(DELETE_MEDIA);
        deleteRegistrationStatement = conn.prepareStatement(DELETE_REGISTRATION);
        deleteRelationshipStatement = conn.prepareStatement(DELETE_RELATIONSHIP);

        // Update statements
        updateBiographyStatement = conn.prepareStatement(UPDATE_BIOGRAPHY);
        updateCardColorStatement = conn.prepareStatement(UPDATE_CARD_COLOR);
        updateCredentialsStatement = conn.prepareStatement(UPDATE_CREDENTIALS);
        updateEducationInformationStatement = conn.prepareStatement(UPDATE_EDUCATION_INFORMATION);
        updateEmailVerificationStatement = conn.prepareStatement(UPDATE_EMAIL_VERIFICATION);
        updatePasswordResetCodeStatement = conn.prepareStatement(UPDATE_PASSWORD_RESET_CODE);
        updatePersonalInformationStatement = conn.prepareStatement(UPDATE_PERSONAL_INFORMATION);
        updateProfilePictureStatement = conn.prepareStatement(UPDATE_PROFILE_PICTURE);
        updateRefreshTokenStatement = conn.prepareStatement(UPDATE_REFRESH_TOKEN);
        updateRelationshipStatement = conn.prepareStatement(UPDATE_RELATIONSHIP);

        // Select statements
        resolveCourseCodeUniversityIdToCourseRecordStatement = conn.prepareStatement(RESOLVE_COURSE_CODE_UNIVERSITY_ID_TO_COURSE_RECORD);
        resolveEmailToUserRecordStatement = conn.prepareStatement(RESOLVE_EMAIL_TO_USER_RECORD);
        resolvePasswordResetCodeToUserRecord = conn.prepareStatement(RESOLVE_PASSWORD_RESET_CODE_TO_USER_RECORD);
        resolveUniversityIdToUniversityRecordStatement = conn.prepareStatement(RESOLVE_UNIVERSITY_ID_TO_UNIVERSITY_RECORD);
        resolveUniversityNameToUniversityRecordStatement = conn.prepareStatement(RESOLVE_UNIVERSITY_NAME_TO_UNIVERSITY_RECORD);
        resolveUserHandleToUserRecordStatement = conn.prepareStatement(RESOLVE_USER_HANDLE_TO_USER_RECORD);
        resolveUserIdOtherUserIdToRelationshipRecordStatement = conn.prepareStatement(RESOLVE_USER_ID_OTHER_USER_ID_TO_RELATIONSHIP_RECORD);
        resolveUserIdToCourseRecordsStatement = conn.prepareStatement(RESOLVE_USER_ID_TO_COURSE_RECORDS);
        resolveUserIdToMediaRecordsStatement = conn.prepareStatement(RESOLVE_USER_ID_TO_MEDIA_RECORDS);
        resolveUserIdToNumberOfFriendsStatement = conn.prepareStatement(RESOLVE_USER_ID_TO_NUMBER_OF_FRIENDS);
        resolveUserIdToRatingStatement = conn.prepareStatement(RESOLVE_USER_ID_TO_RATING);
        resolveUserIdToUserRecordStatement = conn.prepareStatement(RESOLVE_USER_ID_TO_USER_RECORD);
        resolveVerificationCodeToUserRecordStatement = conn.prepareStatement(RESOLVE_VERIFICATION_CODE_TO_USER_RECORD);
    }

    private void closeStatements() throws SQLException {
        // System statements
        systemTransactionCountStatement.close();

        // Create statements
        createCourseStatement.close();
        createMediaStatement.close();
        createRegistrationStatement.close();
        createRelationshipStatement.close();
        createUniversityStatement.close();
        createUserStatement.close();

        // Delete statements
        deleteMediaStatement.close();
        deleteRegistrationStatement.close();
        deleteRelationshipStatement.close();

        // Update statements
        updateBiographyStatement.close();
        updateCardColorStatement.close();
        updateCredentialsStatement.close();
        updateEducationInformationStatement.close();
        updateEmailVerificationStatement.close();
        updatePasswordResetCodeStatement.close();
        updatePersonalInformationStatement.close();
        updateProfilePictureStatement.close();
        updateRefreshTokenStatement.close();
        updateRelationshipStatement.close();

        // Select statements
        resolveCourseCodeUniversityIdToCourseRecordStatement.close();
        resolveEmailToUserRecordStatement.close();
        resolvePasswordResetCodeToUserRecord.close();
        resolveUniversityIdToUniversityRecordStatement.close();
        resolveUniversityNameToUniversityRecordStatement.close();
        resolveUserHandleToUserRecordStatement.close();
        resolveUserIdOtherUserIdToRelationshipRecordStatement.close();
        resolveUserIdToCourseRecordsStatement.close();
        resolveUserIdToMediaRecordsStatement.close();
        resolveUserIdToNumberOfFriendsStatement.close();
        resolveUserIdToRatingStatement.close();
        resolveUserIdToUserRecordStatement.close();
        resolveVerificationCodeToUserRecordStatement.close();
    }

    /**
     * Creates a new user with an unverified email
     *
     * @effect tbl_users (RW), acquires lock
     * @return true / 200 status code iff successfully created new user
     */
    public ResponseEntity<Boolean> transaction_createUser(String userHandle, String name, String email,
                                                          String password, String verificationCode) {

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Checks that email is not mapped to a user record
                ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
                if (resolveEmailToUserRecordRS.next()) {
                    resolveEmailToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
                }
                resolveEmailToUserRecordRS.close();

                byte[] salt = get_salt();
                byte[] hash = get_hash(password, salt);

                // Creates the user
                executeUpdate(createUserStatement, userHandle, name, email, salt, hash, verificationCode);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Gets the user_id for a user_handle
     *
     * @effect tbl_users (R), non-locking
     * @return user_id / 200 status code if user_handle exists. otherwise, return null
     */
    public ResponseEntity<String> transaction_resolveUserHandleToUserId(String userHandle) {
        try {
            // Checks that user handle is not mapped to a user id
            ResultSet resolveUserHandleToUserRecordRS = executeQuery(resolveUserHandleToUserRecordStatement,
                    userHandle);
            if (!resolveUserHandleToUserRecordRS.next()) {
                resolveUserHandleToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
            String userId = resolveUserHandleToUserRecordRS.getString("user_id");
            resolveUserHandleToUserRecordRS.close();

            return new ResponseEntity<>(userId, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the user_name for an email
     *
     * @effect tbl_users (R), non-locking
     * @return user_name / 200 status code if email exists. otherwise, return null
     */
    public ResponseEntity<String> transaction_resolveEmailToUserName(String email) {
        try {
            // Retrieves the user record that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
            String name = resolveEmailToUserRecordRS.getString("user_name");
            resolveEmailToUserRecordRS.close();

            return new ResponseEntity<>(name, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the user_id for an email
     *
     * @effect tbl_users (R), non-locking
     * @return user_id / 200 status code if email exists. otherwise, return null
     */
    public ResponseEntity<String> transaction_resolveEmailToUserId(String email) {
        try {
            // Retrieves the user name that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
            String userId = resolveEmailToUserRecordRS.getString("user_id");
            resolveEmailToUserRecordRS.close();

            return new ResponseEntity<>(userId, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the verification_code for an email
     *
     * @effect tbl_users (R), non-locking
     * @return verification_code / 200 status code if email exists and is unverified.
     *         verification_code / 400 status code if email exists and is verified.
     *         null / 404 status code if email does not exist
     */
    public ResponseEntity<String> transaction_resolveEmailToVerificationCode(String email) {
        try {
            // Retrieves the user record that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }

            String verificationCode = resolveEmailToUserRecordRS.getString("verification_code");
            if (resolveEmailToUserRecordRS.getBoolean("verification_confirmed")) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(verificationCode, HttpStatus.BAD_REQUEST);
            }
            resolveEmailToUserRecordRS.close();
            return new ResponseEntity<>(verificationCode, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the password_reset_code for an email
     *
     * @effect tbl_users (R), non-locking
     * @return password_reset_code / 200 status code if email exists. otherwise, return null
     */
    public ResponseEntity<String> transaction_resolveEmailToPasswordResetCode(String email) {
        try {
            // Retrieves the user record that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }

            String passwordResetCode = resolveEmailToUserRecordRS.getString("password_reset_code");
            resolveEmailToUserRecordRS.close();
            if (passwordResetCode == null) {
                return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(passwordResetCode, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the password_reset_code for an email. If password_reset_code is null, then generate new one
     *
     * @effect tbl_users (RW), locking
     * @return password_reset_code / 200 status code if email exists. otherwise, return null
     */
    public ResponseEntity<String> transaction_generatePasswordResetCode(String email) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Retrieves the user record that the email is mapped to
                ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
                if (!resolveEmailToUserRecordRS.next()) {
                    resolveEmailToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
                }
                resolveEmailToUserRecordRS.close();

                // Generate a new password reset code
                String passwordResetCode = generateSecureString(RESET_CODE_LENGTH);
                executeUpdate(updatePasswordResetCodeStatement, passwordResetCode, email);

                commitTransaction();
                return new ResponseEntity<>(passwordResetCode, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(null, HttpStatus.CONFLICT);
    }

    /**
     * Checks whether the user's email has been verified
     *
     * @effect tbl_users (R), non-locking
     * @return true / 200 status code iff email has been verified
     */
    public ResponseEntity<Boolean> transaction_checkEmailVerified(String email) {
        try {
            // Retrieves the verification code that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(false, HttpStatus.NOT_FOUND);
            } else if (resolveEmailToUserRecordRS.getBoolean("verification_confirmed")) {
                return new ResponseEntity<>(true, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Processes the verification code
     *
     * @effect tbl_users (RW), acquires lock
     * @return true / 200 status code iff user is successfully verified
     */
    public ResponseEntity<Boolean> transaction_processAccountVerificationCode(String verificationCode) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Checks whether verification code exists, has not expired, and has not been used
                ResultSet resolveVerificationCodeToUserRecordRS = executeQuery(resolveVerificationCodeToUserRecordStatement, verificationCode);
                if (!resolveVerificationCodeToUserRecordRS.next() ||
                    parseDateTimeString(resolveVerificationCodeToUserRecordRS.getString("verification_timestamp"))
                        .isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
                    resolveVerificationCodeToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.NOT_FOUND);
                } else if (resolveVerificationCodeToUserRecordRS.getBoolean("verification_confirmed")) {
                    resolveVerificationCodeToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(true, HttpStatus.BAD_REQUEST);
                }

                resolveVerificationCodeToUserRecordRS.close();

                // Verifies the user
                executeUpdate(updateEmailVerificationStatement, verificationCode);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Checks whether the password reset code is valid
     *
     * @effect tbl_users (R), non-locking
     * @return true / 200 status code iff password reset code is valid
     */
    public ResponseEntity<Boolean> transaction_verifyPasswordResetCode(String passwordResetCode) {
        try {
            // Checks whether the password reset code exists, has not expired, and has not been used
            ResultSet checkVerificationCodeUsedRS = executeQuery(resolvePasswordResetCodeToUserRecord, passwordResetCode);
            if (!checkVerificationCodeUsedRS.next() || checkVerificationCodeUsedRS.getString("password_reset_code") == null ||
                parseDateTimeString(checkVerificationCodeUsedRS.getString("password_reset_timestamp"))
                        .isBefore(Instant.now().minus(15, ChronoUnit.MINUTES))) {
                checkVerificationCodeUsedRS.close();
                return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
            }
            checkVerificationCodeUsedRS.close();
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Processes the password reset code
     *
     * @effect tbl_users (RW), acquires lock
     * @return true / 200 status code iff user's credentials have been successfully updated
     */
    public ResponseEntity<Boolean> transaction_processPasswordResetCode(String passwordResetCode, String password) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Checks whether the password reset code exists and has not been used
                ResultSet resolvePasswordResetCodeToUserRecordRS = executeQuery(resolvePasswordResetCodeToUserRecord,
                        passwordResetCode);
                if (!resolvePasswordResetCodeToUserRecordRS.next() || resolvePasswordResetCodeToUserRecordRS.getString("password_reset_code") == null ||
                    parseDateTimeString(resolvePasswordResetCodeToUserRecordRS.getString("password_reset_timestamp"))
                        .isBefore(Instant.now().minus(15, ChronoUnit.MINUTES))) {
                    resolvePasswordResetCodeToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
                }
                String userId = resolvePasswordResetCodeToUserRecordRS.getString("user_id");
                String email = resolvePasswordResetCodeToUserRecordRS.getString("email");
                resolvePasswordResetCodeToUserRecordRS.close();

                byte[] newSalt = get_salt();
                byte[] newHash = get_hash(password, newSalt);

                // Updates the user's credentials
                executeUpdate(updateCredentialsStatement, newSalt, newHash, userId);

                // Disables the password reset code
                executeUpdate(updatePasswordResetCodeStatement, null, email);

                commitTransaction();

                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    e.printStackTrace();
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }

        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Verifies the user's credentials
     *
     * @effect tbl_user (R), non-locking
     * @return true / 200 status code iff user's email and password matches
     */
    public ResponseEntity<Boolean> transaction_verifyCredentials(String email, String password) {
        try {
            // Retrieves the user record that the email is mapped to
            ResultSet resolveEmailToUserRecordRS = executeQuery(resolveEmailToUserRecordStatement, email);
            if (!resolveEmailToUserRecordRS.next()) {
                // If user does not exist, vaguely claim that credentials are incorrect
                resolveEmailToUserRecordRS.close();
                return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
            }

            byte[] salt = resolveEmailToUserRecordRS.getBytes("salt");
            byte[] hash = resolveEmailToUserRecordRS.getBytes("hash");
            resolveEmailToUserRecordRS.close();

            if (Arrays.equals(hash, get_hash(password, salt))) {
                return new ResponseEntity<>(true, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's credentials
     *
     * @effect tbl_user (RW), acquires lock
     * @return true / 200 status code iff user's credentials have been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateCredentials(String userId, String password, String newPassword) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Retrieves the user record that the email is mapped to
                ResultSet resolveEmailToUserRecordRS = executeQuery(resolveUserIdToUserRecordStatement, userId);
                if (!resolveEmailToUserRecordRS.next()) {
                    // If user does not exist, vaguely claim that credentials are incorrect
                    resolveEmailToUserRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
                }

                byte[] salt = resolveEmailToUserRecordRS.getBytes("salt");
                byte[] hash = resolveEmailToUserRecordRS.getBytes("hash");
                resolveEmailToUserRecordRS.close();

                // Check that credentials are correct
                if (!Arrays.equals(hash, get_hash(password, salt))) {
                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
                }

                byte[] newSalt = get_salt();
                byte[] newHash = get_hash(newPassword, newSalt);

                // Updates the user's credentials
                executeUpdate(updateCredentialsStatement, newSalt, newHash, userId);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Verifies the supplied refresh token
     *
     * @effect tbl_user (R), non-locking
     * @return true / 200 status code iff refresh token is valid
     */
    public ResponseEntity<Boolean> transaction_verifyRefreshTokenId(String userId, String tokenId) {
        try {
            // Retrieves the refresh token id that the user id is mapped to
            ResultSet resolveUserIdToUserRecordRS = executeQuery(resolveUserIdToUserRecordStatement, userId);
            if (!resolveUserIdToUserRecordRS.next()) {
                resolveUserIdToUserRecordRS.close();
                return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
            }

            String refreshTokenId = resolveUserIdToUserRecordRS.getString("refresh_token_id");
            resolveUserIdToUserRecordRS.close();

            if (tokenId.equals(refreshTokenId)) {
                return new ResponseEntity<>(true, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(false, HttpStatus.UNAUTHORIZED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Checks whether the supplied token family is current
     *
     * @effect tbl_user (R), non-locking
     * @return true / 200 status code iff token family is current
     */
    public ResponseEntity<Boolean> transaction_verifyRefreshTokenFamily(String userId, String tokenFamily) {
        try {
            // Retrieves the refresh token family that the user id is mapped to
            ResultSet resolveUserIdToUserRecordRS = executeQuery(resolveUserIdToUserRecordStatement, userId);
            if (!resolveUserIdToUserRecordRS.next()) {
                resolveUserIdToUserRecordRS.close();
                return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
            }

            String refreshTokenFamily = resolveUserIdToUserRecordRS.getString("refresh_token_family");
            resolveUserIdToUserRecordRS.close();

            if (tokenFamily.equals(refreshTokenFamily)) {
                return new ResponseEntity<>(true, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's refresh token
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status code iff refresh token has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateRefreshToken(String userId, String refreshTokenId, String refreshTokenFamily) {
        try {
            executeUpdate(updateRefreshTokenStatement, refreshTokenId, refreshTokenFamily, userId);
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's personal information
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status iff user's personal information has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updatePersonalInformation(String userId, String userHandle, String name,
                                                                         String email, String dateOfBirth) {
        try {
            executeUpdate(updatePersonalInformationStatement, userHandle, name, email, dateOfBirth, userId);
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's education information
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status iff user's education information has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateEducationInformation(String userId, String universityName, String major,
                                                                          String standing, String gpa) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                ResultSet resolveUniversityNameToUniversityRecordRS = executeQuery(resolveUniversityNameToUniversityRecordStatement, universityName);
                if (!resolveUniversityNameToUniversityRecordRS.next()) {
                    executeUpdate(createUniversityStatement, universityName);
                    resolveUniversityNameToUniversityRecordRS.close();

                    // Update result set
                    resolveUniversityNameToUniversityRecordRS = executeQuery(resolveUniversityNameToUniversityRecordStatement, universityName);
                    resolveUniversityNameToUniversityRecordRS.next();
                }
                String universityId = resolveUniversityNameToUniversityRecordRS.getString("university_id");
                resolveUniversityNameToUniversityRecordRS.close();

                executeUpdate(updateEducationInformationStatement, universityId, major, standing, gpa, userId);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Updates the user's course registration information
     *
     * @effect tbl_courses (RW), tbl_registration (W), acquires lock
     * @return true / 200 status iff user's course registration information has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateRegistrationInformation(String userId, String universityName, List<String> courseCodes) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // Get university id
                ResultSet resolveUniversityNameToUniversityRecordRS = executeQuery(resolveUniversityNameToUniversityRecordStatement, universityName);
                if (!resolveUniversityNameToUniversityRecordRS.next()) {
                    resolveUniversityNameToUniversityRecordRS.close();
                    return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
                }
                String universityId = resolveUniversityNameToUniversityRecordRS.getString("university_id");
                resolveUniversityNameToUniversityRecordRS.close();

                executeUpdate(deleteRegistrationStatement, userId);

                for (String courseCode : courseCodes) {
                    // If course does not exist, then create new course
                    ResultSet resolveCourseCodeUniversityIdToCourseRecordRS = executeQuery(resolveCourseCodeUniversityIdToCourseRecordStatement, courseCode, universityId);
                    if (!resolveCourseCodeUniversityIdToCourseRecordRS.next()) {
                        executeUpdate(createCourseStatement, courseCode, universityId);
                        resolveCourseCodeUniversityIdToCourseRecordRS.close();

                        // Update result set
                        resolveCourseCodeUniversityIdToCourseRecordRS = executeQuery(resolveCourseCodeUniversityIdToCourseRecordStatement, courseCode, universityId);
                        resolveCourseCodeUniversityIdToCourseRecordRS.next();
                    }
                    String courseId = resolveCourseCodeUniversityIdToCourseRecordRS.getString("course_id");
                    resolveCourseCodeUniversityIdToCourseRecordRS.close();

                    executeUpdate(createRegistrationStatement, userId, courseId);
                }

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Updates the user's biography
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status iff user's biography has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateBiography(String userId, String biography) {
        try {
            executeUpdate(updateBiographyStatement, biography, userId);
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's card color
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status iff user's card color has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateCardColor(String userId, String cardColor) {
        try {
            executeUpdate(updateCardColorStatement, cardColor, userId);
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Updates the user's media
     *
     * @effect tbl_media (W), acquires lock
     * @return true / 200 status iff user's media has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateMedia(String userId, List<String> mediaUrls) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                executeUpdate(deleteMediaStatement, userId);

                for (int i = 0; i < mediaUrls.size(); i++) {
                    String mediaUrl = mediaUrls.get(i);
                    executeUpdate(createMediaStatement, userId, i, mediaUrl);
                }

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Updates the user's profile picture
     *
     * @effect tbl_user (W), non-locking
     * @return true / 200 status iff user's profile picture has been successfully updated
     */
    public ResponseEntity<Boolean> transaction_updateProfilePicture(String userId, String profilePictureUrl) {
        try {
            executeUpdate(updateProfilePictureStatement, profilePictureUrl, userId);
            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * User likes other user
     *
     * @effect tbl_relationships (RW), acquires lock
     * @return relationship_status / 200 status if successfully liked other user
     */
    public ResponseEntity<Boolean> transaction_likeUser(String userId, String otherUserId) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                ResultSet resolveUserIdOtherUserIdToRecordRS = executeQuery(resolveUserIdOtherUserIdToRelationshipRecordStatement, otherUserId, userId);

                if (!resolveUserIdOtherUserIdToRecordRS.next()) {
                    // If other user has no relationship with user, then user likes other user
                    executeUpdate(updateRelationshipStatement, "liked", null, userId, otherUserId);

                } else if (resolveUserIdOtherUserIdToRecordRS.getString("relationship_status").equals("liked")) {
                    // If other user also likes user, then user and other user are now friends
                    executeUpdate(updateRelationshipStatement, "friends", null, userId, otherUserId);
                    executeUpdate(updateRelationshipStatement, "friends", null, otherUserId, userId);
                }
                resolveUserIdOtherUserIdToRecordRS.close();

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * User dislikes other user
     *
     * @effect tbl_relationships (RW), acquires lock
     * @return relationship_status / 200 status if successfully liked other user
     */
    public ResponseEntity<Boolean> transaction_dislikeUser(String userId, String otherUserId) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                ResultSet resolveUserIdOtherUserIdToRecordRS = executeQuery(resolveUserIdOtherUserIdToRelationshipRecordStatement, otherUserId, userId);

                // If other user likes user, then delete that record
                if (resolveUserIdOtherUserIdToRecordRS.next()) {
                    executeUpdate(deleteRegistrationStatement, otherUserId, otherUserId);
                }
                resolveUserIdOtherUserIdToRecordRS.close();

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * User rates other user
     *
     * @effect tbl_relationships (RW), acquires lock
     * @return true / 200 status iff successfully rated other user
     */
    public ResponseEntity<Boolean> transaction_rateUser(String userId, String otherUserId, int rating) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                ResultSet resolveUserIdOtherUserIdToRecordRS = executeQuery(resolveUserIdOtherUserIdToRelationshipRecordStatement, userId, otherUserId);
                if (!resolveUserIdOtherUserIdToRecordRS.next() || !resolveUserIdOtherUserIdToRecordRS.getString("relationship_status").equals("friends")) {
                    resolveUserIdOtherUserIdToRecordRS.close();

                    rollbackTransaction();
                    return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
                }
                resolveUserIdOtherUserIdToRecordRS.close();

                executeUpdate(updateRelationshipStatement, "friends", rating, userId, otherUserId);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * User blocks other user
     *
     * @effect tbl_relationships (W), acquires lock
     * @return true / 200 status iff successfully rated other user
     */
    public ResponseEntity<Boolean> transaction_blockUser(String userId, String otherUserId) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                Integer rating;

                // If user rating exists, then preserve it
                ResultSet resolveUserIdOtherUserIdToRecordRS = executeQuery(resolveUserIdOtherUserIdToRelationshipRecordStatement, userId, otherUserId);
                if (resolveUserIdOtherUserIdToRecordRS.next()) {
                    rating = resolveUserIdOtherUserIdToRecordRS.getInt("rating");
                } else {
                    rating = null;
                }
                resolveUserIdOtherUserIdToRecordRS.close();

                executeUpdate(updateRelationshipStatement, "blocked", rating, userId, otherUserId);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                e.printStackTrace();
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            } finally {
                checkDanglingTransaction();
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Gets the complete profile for user
     *
     * @effect tbl_courses (R), tbl_media (R), tbl_registration (R), tbl_relationships (R), tbl_universities (R), tbl_users (R), non-locking
     * @return User object / 200 status iff successfully retrieved complete profile
     */
    public ResponseEntity<User> transaction_getUser(String userId) {
        try {
            ResultSet resolveUserIdToUserRecordRS = executeQuery(resolveUserIdToUserRecordStatement, userId);
            if (!resolveUserIdToUserRecordRS.next()) {
                resolveUserIdToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
            String userHandle = resolveUserIdToUserRecordRS.getString("user_handle");
            String userName = resolveUserIdToUserRecordRS.getString("user_name");
            String cardColor = resolveUserIdToUserRecordRS.getString("card_color");
            String dateOfBirth = resolveUserIdToUserRecordRS.getString("date_of_birth");
            String universityId = resolveUserIdToUserRecordRS.getString("university_id");
            String major = resolveUserIdToUserRecordRS.getString("major");
            String standing = resolveUserIdToUserRecordRS.getString("standing");
            String gpa = resolveUserIdToUserRecordRS.getString("gpa");
            String biography = resolveUserIdToUserRecordRS.getString("biography");
            String profilePictureUrl = resolveUserIdToUserRecordRS.getString("profile_picture_url");
            resolveUserIdToUserRecordRS.close();

            ResultSet resolveUniversityIdToUniversityRecordRS = executeQuery(resolveUniversityIdToUniversityRecordStatement, universityId);
            String universityName = (!resolveUniversityIdToUniversityRecordRS.next()) ? null : resolveUniversityIdToUniversityRecordRS.getString("university_name");
            resolveUniversityIdToUniversityRecordRS.close();

            ResultSet resolveUserIdToNumberOfFriendsRS = executeQuery(resolveUserIdToNumberOfFriendsStatement, userId);
            String numberOfFriends = (!resolveUserIdToNumberOfFriendsRS.next()) ? null : resolveUserIdToNumberOfFriendsRS.getString("number_of_friends");
            resolveUserIdToNumberOfFriendsRS.close();

            ResultSet resolveUserIdToRatingRS = executeQuery(resolveUserIdToRatingStatement, userId);
            String rating = (!resolveUserIdToRatingRS.next()) ? null : resolveUserIdToRatingRS.getString("rating");
            resolveUserIdToRatingRS.close();

            List<String> mediaUrls = new ArrayList<>();
            ResultSet resolveUserIdToMediaRecordsRS = executeQuery(resolveUserIdToMediaRecordsStatement, userId);
            while (resolveUserIdToMediaRecordsRS.next()) {
                String mediaUrl = resolveUserIdToMediaRecordsRS.getString("media_url");
                mediaUrls.add(mediaUrl);
            }
            resolveUserIdToMediaRecordsRS.close();

            List<String> courseCodes = new ArrayList<>();
            ResultSet resolveUserIdToCourseRecordsRS = executeQuery(resolveUserIdToCourseRecordsStatement, userId);
            while (resolveUserIdToCourseRecordsRS.next()) {
                String courseCode = resolveUserIdToCourseRecordsRS.getString("course_code");
                courseCodes.add(courseCode);
            }
            resolveUserIdToCourseRecordsRS.close();

            User user = new User(userId, userHandle, userName, cardColor, dateOfBirth, universityName, major, standing,
                                 gpa, biography, profilePictureUrl, numberOfFriends, rating, mediaUrls, courseCodes);

            return new ResponseEntity<>(user, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    /**
     * Gets the mini profile for user
     *
     * @effect tbl_users (R), non-locking
     * @return User object / 200 status iff successfully retrieved complete profile
     */
    public ResponseEntity<UserMini> transaction_getUserMini(String userId) {
        try {
            ResultSet resolveUserIdToUserRecordRS = executeQuery(resolveUserIdToUserRecordStatement, userId);
            if (!resolveUserIdToUserRecordRS.next()) {
                resolveUserIdToUserRecordRS.close();
                return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
            }
            String userHandle = resolveUserIdToUserRecordRS.getString("user_handle");
            String userName = resolveUserIdToUserRecordRS.getString("user_name");
            String profilePictureUrl = resolveUserIdToUserRecordRS.getString("profile_picture_url");
            resolveUserIdToUserRecordRS.close();

            UserMini user = new UserMini(userId, userHandle, userName, profilePictureUrl);

            return new ResponseEntity<>(user, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            checkDanglingTransaction();
        }
    }

    public List<String> transaction_loadUsers() {
        throw new NotYetImplementedException();
    }

    public List<String> transaction_searchUsers() {
        throw new NotYetImplementedException();
    }

    /**
     * Starts transaction
     */
    private void beginTransaction() {
        try {
            conn.setAutoCommit(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Commits transaction. disabled when testEnabled and testSavepointEnabled are true
     */
    private void commitTransaction() {
        if (!(testEnabled && testSavepointEnabled)) {
            try {
                conn.commit();
                conn.setAutoCommit(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Rolls-back transaction. disabled when testEnabled and testSavepointEnabled are true
     */
    private void rollbackTransaction() {
        if (!(testEnabled && testSavepointEnabled)) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public int getTransactionCount() {
        try {
            ResultSet systemTransactionCountRS = executeQuery(systemTransactionCountStatement);
            systemTransactionCountRS.next();

            return systemTransactionCountRS.getInt("transaction_count");
        } catch (Exception e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    /**
     * Throw IllegalStateException if transaction not completely complete, rollback
     */
    private void checkDanglingTransaction() {
        try {
            try {
                ResultSet systemTransactionCountRS = executeQuery(systemTransactionCountStatement);
                systemTransactionCountRS.next();

                int transactionCount = systemTransactionCountRS.getInt("transaction_count");

                if ((!testEnabled && transactionCount > 0) || (testEnabled && transactionCount > 1)) {
                    throw new IllegalStateException(
                            "Transaction not fully committed / rolled back. Number of transaction in process: " + transactionCount);
                }
            } finally {
                if (!testEnabled) {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database error", e);
        }
    }

    /**
     * Creates a savepoint. throws an IllegalStateException if testing is not enabled
     */
    public Savepoint createSavepoint() throws SQLException {
        if (testEnabled) {
            testSavepointEnabled = true;

            conn.setAutoCommit(false);
            return conn.setSavepoint("savepoint");

        } else {
            throw new IllegalStateException("Enable testing to create savepoints");
        }
    }

    /**
     * Reverts to a savepoint. throws an IllegalStateException if testing is not enabled
     */
    public void revertToSavepoint(Savepoint savepoint) throws SQLException {
        if (testEnabled) {
            testSavepointEnabled = false;

            conn.rollback(savepoint);
            conn.commit();
            conn.setAutoCommit(true);

        } else {
            throw new IllegalStateException("Enable testing to revert to savepoints");
        }
    }

    /**
     * Checks whether the exception is caused by a transaction deadlock
     *
     * @return true iff the exception is caused by a transaction deadlock
     */
    private static boolean isDeadLock(Exception e) {
        if (e instanceof SQLException) {
            return ((SQLException) e).getErrorCode() == 1205;
        }
        return false;
    }

    /**
     * Sets the statement's parameters to the method's arguments in the order they are passed in
     *
     * @param statement canned SQL statement
     * @param args      statement parameters
     */
    private void setParameters(PreparedStatement statement, Object... args) throws SQLException {
        int parameterIndex = 1;
        statement.clearParameters();
        for (Object arg : args) {
            if (arg == null) {
                statement.setNull(parameterIndex, Types.NULL);
            } else {
                statement.setObject(parameterIndex, arg);
            }
            parameterIndex++;
        }
    }

    /**
     * Executes the query statement with the specified parameters
     *
     * @param statement canned SQL statement
     * @param args      statement parameters
     * @return query results as a ResultSet
     */
    private ResultSet executeQuery(PreparedStatement statement, Object... args) throws SQLException {
        setParameters(statement, args);
        return statement.executeQuery();
    }

    /**
     * Executes the update statement with the specified parameters
     *
     * @param statement canned SQL statement
     * @param args      statement parameters
     */
    private void executeUpdate(PreparedStatement statement, Object... args) throws SQLException {
        setParameters(statement, args);
        statement.executeUpdate();
    }

    /**
     * Generates a random cryptographic salt
     *
     * @return cryptographic salt
     */
    private byte[] get_salt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    /**
     * Generates a cryptographic hash
     *
     * @param password password to be hashed
     * @param salt     salt for the hash
     * @return cryptographic hash
     */
    private byte[] get_hash(String password, byte[] salt) {
        // Specify the hash parameters
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return factory.generateSecret(spec).getEncoded();

        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException();
        }
    }

    /**
     * Parses a DateTime string
     *
     * @param dateTime in yyyy-MM-dd HH:mm:ss.SSS format
     * @return Instant object representing UTC time
     */
    private Instant parseDateTimeString(String dateTime) {
        return LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                                                              .atZone(ZoneId.of("UTC"))
                                                              .toInstant();
    }
}
