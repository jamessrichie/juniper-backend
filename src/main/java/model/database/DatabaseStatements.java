package model.database;

public final class DatabaseStatements {

    // Counts number of active transactions on the current connection
    public static final String SYSTEM_TRANSACTION_COUNT = "SELECT @@TRANCOUNT AS transaction_count";

    // Creates a course record
    public static final String CREATE_COURSE = "INSERT INTO tbl_courses " +
                                               "VALUES (NEWID(), ?, ?)";

    // Creates a user media record
    public static final String CREATE_MEDIA = "INSERT INTO tbl_media " +
                                              "VALUES (?, ?, ?)";

    // Creates a user course registration record
    public static final String CREATE_REGISTRATION = "INSERT INTO tbl_registration " +
                                                     "VALUES (NEWID(), ?, ?)";

    // Creates a relationship record between two users
    public static final String CREATE_RELATIONSHIP = "INSERT INTO tbl_relationship " +
                                                     "VALUES (NEWID(), ?, ?, ?, ?)";

    // Creates a university record
    public static final String CREATE_UNIVERSITY = "INSERT INTO tbl_universities " +
                                                   "VALUES (NEWID(), ?)";

    // Creates a user record
    public static final String CREATE_USER = "INSERT INTO tbl_users " +
                                             "VALUES (NEWID(), ?, ?, ?, ?, ?, null, null, ?, GETUTCDATE(), 0," +
                                             "null, null, 'verification', 0, null, null, null, null, null, null, null, null)";

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

    // Removes an unverified user
    public static final String DELETE_UNVERIFIED_USER = "DELETE FROM tbl_users " +
                                                        "WHERE email = ? " +
                                                        "AND verification_confirmed = 0";

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

    // Sets a user's date of birth field
    public static final String UPDATE_DATE_OF_BIRTH = "UPDATE tbl_users " +
                                                      "SET date_of_birth = ? " +
                                                      "WHERE user_id = ?";

    // Sets a user's university_id, major, standing, and gpa fields
    public static final String UPDATE_EDUCATION_INFORMATION = "UPDATE tbl_users " +
                                                              "SET university_id = ?, major = ?, " +
                                                              "standing = ?, gpa = ? " +
                                                              "WHERE user_id = ?";

    // Sets a user's has_verified_email field
    public static final String UPDATE_EMAIL_VERIFICATION = "UPDATE tbl_users " +
                                                           "SET verification_confirmed = 1 " +
                                                           "WHERE verification_code = ?";

    // Set's a user's password_reset_code, password_reset_timestamp fields
    public static final String UPDATE_PASSWORD_RESET_CODE = "UPDATE tbl_users " +
                                                            "SET password_reset_code = ?, " +
                                                            "password_reset_timestamp = GETUTCDATE()," +
                                                            "most_recent_email_type = 'password_reset'" +
                                                            "WHERE email = ?";

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

    // Gets the course record for a course_code, university_id pair
    public static final String RESOLVE_COURSE_CODE_UNIVERSITY_ID_TO_COURSE_RECORD = "SELECT * FROM tbl_courses " +
                                                                                  "WHERE course_code = ? " +
                                                                                  "AND university_id = ?";

    // Gets the user record for an email
    public static final String RESOLVE_EMAIL_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                              "WHERE email = ?";

    // Gets the user record for a password reset code
    public static final String RESOLVE_PASSWORD_RESET_CODE_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                            "WHERE password_reset_code = ?";

    // Gets the university record for a university_id
    public static final String RESOLVE_UNIVERSITY_ID_TO_UNIVERSITY_RECORD = "SELECT * FROM tbl_universities " +
                                                                            "WHERE university_id = ?";

    // Gets the university record for a university_name
    public static final String RESOLVE_UNIVERSITY_NAME_TO_UNIVERSITY_RECORD = "SELECT * FROM tbl_universities " +
                                                                              "WHERE university_name = ?";

    // Gets the user record for a user_handle
    public static final String RESOLVE_USER_HANDLE_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                    "WHERE user_handle = ?";

    // Gets the relationship record for a user_id, other_user_id pair
    public static final String RESOLVE_USER_ID_OTHER_USER_ID_TO_RELATIONSHIP_RECORD = "SELECT * FROM tbl_relationships " +
                                                                                      "WHERE user_id = ? " +
                                                                                      "AND other_user_id ?";

    // Gets the course records for a user_id
    public static final String RESOLVE_USER_ID_TO_COURSE_RECORDS = "SELECT tbl_courses.* " +
                                                                   "FROM tbl_courses, tbl_registration " +
                                                                   "WHERE tbl_registration.user_id = ? " +
                                                                   "AND tbl_courses.course_id = tbl_registration.course_id " +
                                                                   "ORDER BY tbl_courses.course_code";

    // Gets the media records for a user_id
    public static final String RESOLVE_USER_ID_TO_MEDIA_RECORDS = "SELECT * FROM tbl_media " +
                                                                  "WHERE user_id = ? " +
                                                                  "ORDER BY ordering";

    // Gets the number of friends for a user_id
    public static final String RESOLVE_USER_ID_TO_NUMBER_OF_FRIENDS = "SELECT COUNT(*) AS number_of_friends " +
                                                                      "FROM tbl_relationships " +
                                                                      "WHERE user_id = ? " +
                                                                      "AND relationship_status = 'friends'";

    // Gets the rating for a user_id
    public static final String RESOLVE_USER_ID_TO_RATING = "SELECT AVG(rating) AS rating " +
                                                           "FROM tbl_relationships " +
                                                           "WHERE user_id = ?";

    // Gets the user record for a user_id
    public static final String RESOLVE_USER_ID_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                "WHERE user_id = ?";

    // Gets the user record for a verification code
    public static final String RESOLVE_VERIFICATION_CODE_TO_USER_RECORD = "SELECT * FROM tbl_users " +
                                                                          "WHERE verification_code = ?";
}
