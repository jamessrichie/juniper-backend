package model;

public final class DatabaseStatements {

    // Creates a course
    public static final String CREATE_COURSE = "INSERT INTO tbl_courses " +
                                                "VALUES (?, ?)";

    // Creates a user media record
    public static final String CREATE_MEDIA = "INSERT INTO tbl_media " +
                                               "VALUES (?, ?, ?)";

    // Creates a user course registration record
    public static final String CREATE_REGISTRATION = "INSERT INTO tbl_registration " +
                                                      "VALUES (?, ?, ?)";

    // Creates a relationship record between two users
    public static final String CREATE_RELATIONSHIP = "INSERT INTO tbl_relationship " +
                                                      "VALUES(?, ?, ?, ?)";

    // Creates a user
    public static final String CREATE_USER = "INSERT INTO tbl_users " +
                                              "VALUES (?, ?, ?, ?, ?, ?, null, null, null, ?, UTC_TIMESTAMP(), false, " +
                                              "false, null, null, null, null, null, null, null)";

    // Removes a user's media
    public static final String DELETE_MEDIA = "DELETE FROM tbl_media " +
                                               "WHERE user_id = ?";

    // Removes a user's registration data
    public static final String DELETE_REGISTRATION = "DELETE FROM tbl_registration " +
                                                      "WHERE user_id = ?";

    // Removes a user's relationship record
    public static final String DELETE_RELATIONSHIP = "DELETE FROM tbl_relationship " +
                                                     "WHERE user_id = ? " +
                                                     "AND other_user_id = ?";

    // Sets a user's biography field
    public static final String UPDATE_BIOGRAPHY = "UPDATE tbl_users " +
                                                   "SET biography = ? " +
                                                   "WHERE user_id = ?";

    // Sets a user's card_color field
    public static final String UPDATE_CARD_COLOR = "UPDATE tbl_users " +
                                                    "SET card_color = ? " +
                                                    "WHERE user_id = ?";

    // Sets a user's salt and hash fields
    public static final String UPDATE_CREDENTIALS = "UPDATE tbl_users " +
                                                     "SET salt = ?, hash = ? " +
                                                     "WHERE user_id = ?";

    // Sets a user's university_id, major, standing, and gpa fields
    public static final String UPDATE_EDUCATION_INFORMATION = "UPDATE tbl_users " +
                                                               "SET university_id = ?, major = ?, " +
                                                               "standing = ?, gpa = ? " +
                                                               "WHERE user_id = ?";

    // Sets a user's has_verified_email field
    public static final String UPDATE_EMAIL_VERIFICATION = "UPDATE tbl_users " +
                                                            "SET has_verified_email = ? " +
                                                            "WHERE verification_code = ?";

    // Sets a user's user_handle, user_name, email, date_of_birth, city, state, and country fields
    public static final String UPDATE_PERSONAL_INFORMATION = "UPDATE tbl_users " +
                                                              "SET user_handle = ?, user_name = ?, " +
                                                              "email = ?, date_of_birth = ? " +
                                                              "WHERE user_id = ?";

    // Sets a user's profile_picture_url field
    public static final String UPDATE_PROFILE_PICTURE = "UPDATE tbl_users " +
                                                         "SET profile_picture_url = ? " +
                                                         "WHERE user_id = ?";

    // Sets a user's refresh_token_id and refresh_token_family fields
    public static final String UPDATE_REFRESH_TOKEN = "UPDATE tbl_users " +
                                                       "SET refresh_token_id = ?, refresh_token_family = ? " +
                                                       "WHERE user_id = ?";

    // Sets a user's relationship_status and rating fields
    public static final String UPDATE_RELATIONSHIP = "UPDATE tbl_relationships " +
                                                     "SET relationship_status = ?, rating = ? " +
                                                     "WHERE user_id = ? " +
                                                     "AND other_user_id = ?";

    // Gets the course record for a course_id, university_id pair
    public static final String RESOLVE_COURSE_ID_UNIVERSITY_ID_TO_COURSE_RECORD = "SELECT * FROM tbl_courses " +
                                                                                   "WHERE course_id = ? " +
                                                                                   "AND university_id = ?";

    // Gets the user record for an email
    public static final String RESOLVE_EMAIL_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                               "WHERE email = ?";

    // Gets the user record for a user_handle
    public static final String RESOLVE_USER_HANDLE_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                     "WHERE user_handle = ?";

    // Gets the relationship record for a user_id, other_user_id pair
    public static final String RESOLVE_USER_ID_OTHER_USER_ID_TO_RECORD = "SELECT * FROM tbl_relationships " +
                                                                           "WHERE user_id = ? " +
                                                                           "AND other_user_id ?";

    // Gets the user record for a user_id
    public static final String RESOLVE_USER_ID_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                 "WHERE user_id = ?";

    // Checks if the email has been verified
    public static final String CHECK_EMAIL_VERIFICATION = "SELECT has_verified_email FROM tbl_users " +
                                                           "WHERE email = ?";

    // Checks if verification code is active. If false, then either code has been used, or has expired
    public static final String CHECK_VERIFICATION_CODE_ACTIVE = "SELECT EXISTS(" +
                                                                     "SELECT * FROM tbl_users " +
                                                                     "WHERE verification_code = ? " +
                                                                     "AND has_verified_email = 0) " +
                                                                 "AS verification_code_active";

    // Checks if verification code has been used. If false, then either code is still active, or has expired
    public static final String CHECK_VERIFICATION_CODE_USED = "SELECT EXISTS(" +
                                                                   "SELECT * FROM tbl_users " +
                                                                   "WHERE verification_code = ? " +
                                                                   "AND has_verified_email = 1) " +
                                                                "AS verification_code_used";

    
}
