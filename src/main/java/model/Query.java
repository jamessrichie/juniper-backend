package model;

import java.io.*;
import java.sql.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.util.ResourceUtils;

import exceptions.*;

public class Query {

    // Database connection
    private final Connection conn;

    // Password hashing parameter constants
    private static final int HASH_STRENGTH = 65536;
    private static final int KEY_LENGTH = 128;

    // Number of attempts when encountering deadlock
    private static final int MAX_ATTEMPTS = 16;

    // Creates a user
    private static final String CREATE_USER = "INSERT INTO tbl_users VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), false, false, null, null, null, null, null, 0, 0, null, null, null, null, null)";
    private PreparedStatement createUserStatement;

    // Checks if an email is in use
    private static final String CHECK_EMAIL_IN_USE = "SELECT EXISTS(SELECT * FROM tbl_users WHERE email = ?) AS email_in_use";
    private PreparedStatement checkEmailInUseStatement;

    // Gets the verification code for a user (if does not exist, then already expired)
    private static final String GET_VERIFICATION_CODE = "SELECT verification_code FROM tbl_users WHERE email = ?";
    private PreparedStatement getVerificationCodeStatement;

    // Gets the name for a user
    private static final String GET_USER_NAME = "SELECT name FROM tbl_users WHERE email = ?";
    private PreparedStatement getUserNameStatement;

    // Checks whether the verification code has been used (if false, then either code has not been used or has expired)
    private static final String CHECK_VERIFICATION_CODE_USED = "SELECT EXISTS(SELECT * FROM tbl_users WHERE verification_code = ? AND has_verified_email = 1) AS verification_code_used";
    private PreparedStatement checkVerificationCodeUsedStatement;

    // Checks whether the verification code is still active (if false, then either code has been used or has expired)
    private static final String CHECK_VERIFICATION_CODE_ACTIVE = "SELECT EXISTS(SELECT * FROM tbl_users WHERE verification_code = ? AND has_verified_email = 0) AS verification_code_active";
    private PreparedStatement checkVerificationCodeActiveStatement;

    // Verifies a user
    private static final String VERIFY_USER = "UPDATE tbl_users SET has_verified_email = 1 WHERE verification_code = ?";
    private PreparedStatement verifyUserStatement;


    public Query() throws SQLException, IOException {
        conn = openConnectionFromDbConn();
        prepareStatements();
    }

    /**
     * Return the connection specified by the dbconn.properties file
     */
    private static Connection openConnectionFromDbConn() throws SQLException, IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:connections/dbconn.properties")));

        String endpoint = configProps.getProperty("RDS_ENDPOINT");
        String port = configProps.getProperty("RDS_PORT");
        String dbName = configProps.getProperty("RDS_DB_NAME");
        String adminName = configProps.getProperty("RDS_USERNAME");
        String password = configProps.getProperty("RDS_PASSWORD");

        String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s", endpoint, port, dbName, adminName, password);
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
        conn.close();
    }

    /*
     * Prepare all the SQL statements in this method
     */
    private void prepareStatements() throws SQLException {
        createUserStatement = conn.prepareStatement(CREATE_USER);
        checkEmailInUseStatement = conn.prepareStatement(CHECK_EMAIL_IN_USE);
        getVerificationCodeStatement = conn.prepareStatement(GET_VERIFICATION_CODE);
        getUserNameStatement = conn.prepareStatement(GET_USER_NAME);
        checkVerificationCodeUsedStatement = conn.prepareStatement(CHECK_VERIFICATION_CODE_USED);
        checkVerificationCodeActiveStatement = conn.prepareStatement(CHECK_VERIFICATION_CODE_ACTIVE);
        verifyUserStatement = conn.prepareStatement(VERIFY_USER);
    }

    /**
     * Creates a new user with an unverified email
     *
     * @return HTTP status code with message
     */
    public ResponseEntity<String> transaction_createUser(String userID, String userHandle, String name, String email,
                                                         byte[] salt, byte[] hash, String verificationCode) {

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // checks whether the supplied email is in use
                ResultSet checkEmailInUseRS = executeQuery(checkEmailInUseStatement, email);
                checkEmailInUseRS.next();
                if (checkEmailInUseRS.getBoolean("email_in_use")) {
                    return new ResponseEntity<>("Email is already in use\n", HttpStatus.BAD_REQUEST);
                }
                checkEmailInUseRS.close();

                // creates the user
                executeUpdate(createUserStatement, userID, userHandle, name, email, salt, hash, verificationCode);

                commitTransaction();
                return new ResponseEntity<>("Successfully created user\n", HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>("Failed to create user" + e + "\n", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>("Server deadlock\nm", HttpStatus.CONFLICT);
    }

    /**
     * Gets the name for a specified user
     *
     * @return HTTP status code with message. If status code OK, the message contains the user name
     */
    public ResponseEntity<String> transaction_getUserName(String email) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // gets the name
                ResultSet getUserNameRS = executeQuery(getUserNameStatement, email);
                if (!getUserNameRS.next()) {
                    return new ResponseEntity<>("No user associated with the provided email", HttpStatus.BAD_REQUEST);
                }
                String name = getUserNameRS.getString("name");
                getUserNameRS.close();

                commitTransaction();
                return new ResponseEntity<>(name, HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>("Failed to retrieve user name\n", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>("Server deadlock\nm", HttpStatus.CONFLICT);
    }

    /**
     * Gets the verification code for a specified user
     *
     * @return HTTP status code with message. If status code OK, the message contains the verification code
     */
    public ResponseEntity<String> transaction_getVerificationCode(String email) {

        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // gets the verification code associated with the email
                ResultSet getVerificationCodeRS = executeQuery(getVerificationCodeStatement, email);
                if (!getVerificationCodeRS.next()) {
                    return new ResponseEntity<>("The verification code has expired", HttpStatus.GONE);
                }
                String verificationCode = getVerificationCodeRS.getString("verification_code");
                getVerificationCodeRS.close();

                // checks whether the verification code has been used
                ResultSet checkVerificationCodeUsedRS = executeQuery(checkVerificationCodeUsedStatement, verificationCode);
                checkVerificationCodeUsedRS.next();
                if (checkVerificationCodeUsedRS.getBoolean("verification_code_used")) {
                    return new ResponseEntity<>("The verification code has already been activated", HttpStatus.BAD_REQUEST);
                }
                checkVerificationCodeUsedRS.close();

                commitTransaction();
                return new ResponseEntity<>(verificationCode, HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>("Failed to retrieve verification code\n", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>("Server deadlock\nm", HttpStatus.CONFLICT);
    }

    /**
     * Verifies the user associated with the verification code
     *
     * @return HTTP status code with message
     */
    public ResponseEntity<String> transaction_verifyUser(String verificationCode) {
        /**
         * Check if verification code has expired or user has been verified. Return error codes as appropriate. If neither, then verify and return success
         *
         */
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // checks whether the verification code has been used
                ResultSet checkVerificationCodeUsedRS = executeQuery(checkVerificationCodeUsedStatement, verificationCode);
                checkVerificationCodeUsedRS.next();
                if (checkVerificationCodeUsedRS.getBoolean("verification_code_used")) {
                    return new ResponseEntity<>("The verification code has already been activated", HttpStatus.BAD_REQUEST);
                }
                checkVerificationCodeUsedRS.close();

                // checks whether the verification code is still active
                ResultSet checkVerificationCodeActiveRS = executeQuery(checkVerificationCodeActiveStatement, verificationCode);
                checkVerificationCodeActiveRS.next();
                if (!checkVerificationCodeActiveRS.getBoolean("verification_code_active")) {
                    return new ResponseEntity<>("The verification code has expired", HttpStatus.GONE);
                }
                checkVerificationCodeActiveRS.close();

                // verifies the user
                executeUpdate(verifyUserStatement, verificationCode);

                commitTransaction();
                return new ResponseEntity<>("Successfully verified user\n", HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>("Failed to verify user\n", HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>("Server deadlock\nm", HttpStatus.CONFLICT);
    }

    public Boolean transaction_updateUserCredentials() {
        throw new NotYetImplementedException();
    }

    public Boolean transaction_updateUserProfile() {
        throw new NotYetImplementedException();
    }

    public List<String> transaction_loadUsers() {
        throw new NotYetImplementedException();
    }

    public Boolean transaction_rateUser() {
        throw new NotYetImplementedException();
    }

    public Boolean transaction_likeUser() {
        throw new NotYetImplementedException();
    }

    public Boolean transaction_dislikeUser() {
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
     * Commits transaction
     */
    private void commitTransaction() {
        try {
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Rolls-back transaction
     */
    private void rollbackTransaction() {
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks whether the exception is caused by a transaction deadlock
     *
     * @param e exception
     * @return true iff the exception is caused by a transaction deadlock
     */
    private static boolean isDeadLock(Exception e) {
        if (e instanceof SQLException) {
            return ((SQLException) e).getErrorCode() == 1205;
        }
        return false;
    }

    /**
     * Sets the query's parameters to the method's arguments in the order they are passed in
     *
     * @param statement canned SQL query
     * @param args query parameters
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
     * @param statement canned SQL query
     * @param args query parameters
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
     * @param args query parameters
     */
    private void executeUpdate(PreparedStatement statement, Object... args) throws SQLException {
        setParameters(statement, args);
        statement.executeUpdate();
    }
}
