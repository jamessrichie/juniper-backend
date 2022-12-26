CREATE TABLE tbl_universities
(
    university_id   varchar(36)  NOT NULL,
    university_name varchar(256) NOT NULL,

    PRIMARY KEY (university_id)

) ENGINE = INNODB;


CREATE TABLE tbl_courses
(
    course_id     varchar(36) NOT NULL,
    university_id varchar(36) NOT NULL,

    PRIMARY KEY (course_id, university_id),
    FOREIGN KEY (university_id) REFERENCES tbl_universities (university_id) ON UPDATE CASCADE ON DELETE RESTRICT

) ENGINE = INNODB;


CREATE TABLE tbl_users
(
    user_id                  varchar(36)   NOT NULL,
    user_handle              varchar(261)  NOT NULL,
    user_name                varchar(256)  NOT NULL,
    email                    varchar(256)  NOT NULL,
    salt                     varbinary(16) NOT NULL,
    hash                     varbinary(20) NOT NULL,
    refresh_token_id         varchar(36),
    refresh_token_family     varchar(36),
    profile_picture_url      varchar(512),
    verification_code        varchar(64)   NOT NULL,
    verification_timestamp   datetime      NOT NULL,
    has_verified_email       boolean       NOT NULL,
    password_reset_code      varchar(64),
    password_reset_timestamp datetime,
    has_reset_password       boolean,
    has_valid_profile        boolean       NOT NULL,
    card_color               varchar(15),
    date_of_birth            date,
    university_id            varchar(36),
    major                    varchar(64),
    standing                 varchar(32),
    gpa                      varchar(4),
    biography                text,

    PRIMARY KEY (user_id),
    FOREIGN KEY (university_id) REFERENCES tbl_universities (university_id) ON UPDATE CASCADE ON DELETE RESTRICT,
    UNIQUE (user_handle),
    UNIQUE (email),
    UNIQUE (verification_code)

) ENGINE = INNODB;


CREATE TABLE tbl_registration
(
    user_id       varchar(36) NOT NULL,
    course_id     varchar(36) NOT NULL,
    university_id varchar(36) NOT NULL,

    PRIMARY KEY (user_id, course_id, university_id),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (university_id) REFERENCES tbl_courses (university_id) ON UPDATE CASCADE ON DELETE CASCADE

) ENGINE = INNODB;


CREATE TABLE tbl_media
(
    user_id   varchar(36)  NOT NULL,
    ordering  int          NOT NULL,
    media_url varchar(512) NOT NULL,

    PRIMARY KEY (user_id, ordering),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE CASCADE ON DELETE CASCADE

) ENGINE = INNODB;


CREATE TABLE tbl_relationships
(
    user_id             varchar(36) NOT NULL,
    other_user_id       varchar(36) NOT NULL,
    relationship_status varchar(32) NOT NULL,
    rating              int,

    PRIMARY KEY (user_id, other_user_id),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE CASCADE ON DELETE CASCADE,
    FOREIGN KEY (other_user_id) REFERENCES tbl_users (user_id) ON UPDATE CASCADE ON DELETE CASCADE

) ENGINE = INNODB;

CREATE INDEX idx_users_verification_timestamp ON tbl_users (verification_timestamp);