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

    // Number of attempts when encountering deadlock
    private static final int MAX_ATTEMPTS = 16;

    // Creates a user
    private static final String CREATE_USER = "INSERT INTO tbl_users VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), false, false, null, null, null, null, null, 0, 0, null, null, null, null, null)";
    private PreparedStatement createUserStatement;

    // Maps email -> verification code
    // if verification code does not exist, then invalid
    private static final String RESOLVE_EMAIL_TO_VERIFICATION_CODE = "SELECT verification_code FROM tbl_users WHERE email = ?";
    private PreparedStatement resolveEmailToVerificationCodeStatement;

    // Checks if verification code has been used
    // if false, then either code is still active, or has expired
    private static final String CHECK_VERIFICATION_CODE_USED = "SELECT EXISTS(SELECT * FROM tbl_users WHERE verification_code = ? AND has_verified_email = 1) AS verification_code_used";
    private PreparedStatement checkVerificationCodeUsedStatement;

    // Checks if verification code is active
    // if false, then either code has been used, or has expired
    private static final String CHECK_VERIFICATION_CODE_ACTIVE = "SELECT EXISTS(SELECT * FROM tbl_users WHERE verification_code = ? AND has_verified_email = 0) AS verification_code_active";
    private PreparedStatement checkVerificationCodeActiveStatement;

    // Verifies a user
    private static final String VERIFY_USER = "UPDATE tbl_users SET has_verified_email = 1 WHERE verification_code = ?";
    private PreparedStatement verifyUserStatement;

    // Maps user handle -> user id
    private static final String RESOLVE_USER_HANDLE_TO_USER_ID = "SELECT user_id FROM tbl_users WHERE user_handle = ?";
    private PreparedStatement resolveUserHandleToUserIdStatement;

    // Maps email -> user id
    private static final String RESOLVE_EMAIL_TO_USER_ID = "SELECT user_id FROM tbl_users WHERE email = ?";
    private PreparedStatement resolveEmailToUserIdStatement;

    // Maps email -> username
    private static final String RESOLVE_EMAIL_TO_USER_NAME = "SELECT user_name FROM tbl_users WHERE email = ?";
    private PreparedStatement resolveEmailToUserNameStatement;


    public Query() throws SQLException, IOException {
        conn = openConnectionFromDbConn();
        prepareStatements();
    }

    /**
     * Return the connection specified by the dbconn.properties file
     */
    private static Connection openConnectionFromDbConn() throws SQLException, IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/dbconn.properties")));

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
        resolveEmailToVerificationCodeStatement = conn.prepareStatement(RESOLVE_EMAIL_TO_VERIFICATION_CODE);
        checkVerificationCodeUsedStatement = conn.prepareStatement(CHECK_VERIFICATION_CODE_USED);
        checkVerificationCodeActiveStatement = conn.prepareStatement(CHECK_VERIFICATION_CODE_ACTIVE);
        verifyUserStatement = conn.prepareStatement(VERIFY_USER);
        resolveUserHandleToUserIdStatement = conn.prepareStatement(RESOLVE_USER_HANDLE_TO_USER_ID);
        resolveEmailToUserIdStatement = conn.prepareStatement(RESOLVE_EMAIL_TO_USER_ID);
        resolveEmailToUserNameStatement = conn.prepareStatement(RESOLVE_EMAIL_TO_USER_NAME);
    }

    /**
     * Gets the user id for a user handle
     *
     * @return if user handle exists, return user id. otherwise return null
     */
    public ResponseEntity<String> transaction_userHandleToUserIdResolution(String userHandle) {
        try{
            ResultSet resolveUserHandleToUserIdRS = executeQuery(resolveUserHandleToUserIdStatement, userHandle);

            // checks that user handle is not mapped to a user id
            if (!resolveUserHandleToUserIdRS.next()) {
                resolveUserHandleToUserIdRS.close();
                return new ResponseEntity<>(null, HttpStatus.OK);
            }
            String userId = resolveUserHandleToUserIdRS.getString("user_id");
            resolveUserHandleToUserIdRS.close();

            return new ResponseEntity<>(userId, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Creates a new user with an unverified email
     *
     * @return true iff successfully created new user
     */
    public ResponseEntity<Boolean> transaction_createUser(String userID, String userHandle, String name, String email,
                                                         byte[] salt, byte[] hash, String verificationCode) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // checks that email is not mapped to a user id
                ResultSet resolveEmailToUserIdRS = executeQuery(resolveEmailToUserIdStatement, email);
                if (resolveEmailToUserIdRS.next()) {
                    resolveEmailToUserIdRS.close();
                    return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
                }
                resolveEmailToUserIdRS.close();

                // checks that user handle is not mapped to a user id
                if (transaction_userHandleToUserIdResolution(userHandle).getBody() != null) {
                    return new ResponseEntity<>(false, HttpStatus.BAD_REQUEST);
                }

                // creates the user
                executeUpdate(createUserStatement, userID, userHandle, name, email, salt, hash, verificationCode);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
    }

    /**
     * Gets the name for a specified user
     *
     * @return if email exists, return name. otherwise return null
     */
    public ResponseEntity<String> transaction_getUserName(String email) {
        try {
            // retrieves the username that the email is mapped to
            ResultSet resolveEmailToUserNameRS = executeQuery(resolveEmailToUserNameStatement, email);
            if (!resolveEmailToUserNameRS.next()) {
                resolveEmailToUserNameRS.close();
                return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
            }
            String name = resolveEmailToUserNameRS.getString("name");
            resolveEmailToUserNameRS.close();

            return new ResponseEntity<>(name, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Gets the verification code for a specified user
     *
     * @return if account exists, return verification code. otherwise return null
     */
    public ResponseEntity<String> transaction_getVerificationCode(String email) {
        try {
            // gets the verification code associated with the email
            ResultSet getVerificationCodeRS = executeQuery(resolveEmailToVerificationCodeStatement, email);
            if (!getVerificationCodeRS.next()) {
                return new ResponseEntity<>(null, HttpStatus.GONE);
            }
            String verificationCode = getVerificationCodeRS.getString("verification_code");
            getVerificationCodeRS.close();

            // checks whether the verification code has been used
            ResultSet checkVerificationCodeUsedRS = executeQuery(checkVerificationCodeUsedStatement, verificationCode);
            checkVerificationCodeUsedRS.next();
            if (checkVerificationCodeUsedRS.getBoolean("verification_code_used")) {
                return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
            }
            checkVerificationCodeUsedRS.close();

            commitTransaction();
            return new ResponseEntity<>(verificationCode, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Verifies the user associated with the verification code
     *
     * @return return true iff user has been verified
     */
    public ResponseEntity<Boolean> transaction_verifyUser(String verificationCode) {
        for (int attempts = 0; attempts < MAX_ATTEMPTS; attempts++) {
            try {
                beginTransaction();

                // checks whether the verification code has been used
                // this check is done to distinguish between expired vs used verification codes
                ResultSet checkVerificationCodeUsedRS = executeQuery(checkVerificationCodeUsedStatement, verificationCode);
                checkVerificationCodeUsedRS.next();
                if (checkVerificationCodeUsedRS.getBoolean("verification_code_used")) {
                    checkVerificationCodeUsedRS.close();
                    return new ResponseEntity<>(true, HttpStatus.BAD_REQUEST);
                }
                checkVerificationCodeUsedRS.close();

                // checks whether the verification code exists and is still active
                ResultSet checkVerificationCodeActiveRS = executeQuery(checkVerificationCodeActiveStatement, verificationCode);
                checkVerificationCodeActiveRS.next();
                if (!checkVerificationCodeActiveRS.getBoolean("verification_code_active")) {
                    checkVerificationCodeActiveRS.close();
                    return new ResponseEntity<>(false, HttpStatus.GONE);
                }
                checkVerificationCodeActiveRS.close();

                // verifies the user
                executeUpdate(verifyUserStatement, verificationCode);

                commitTransaction();
                return new ResponseEntity<>(true, HttpStatus.OK);

            } catch (Exception e) {
                rollbackTransaction();

                if (!isDeadLock(e)) {
                    return new ResponseEntity<>(false, HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
        return new ResponseEntity<>(false, HttpStatus.CONFLICT);
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
