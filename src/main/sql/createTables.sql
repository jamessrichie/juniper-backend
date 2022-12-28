CREATE TABLE tbl_universities
(
    university_id   varchar(36)  NOT NULL,
    university_name varchar(256) NOT NULL,

    PRIMARY KEY (university_id),
    UNIQUE (university_name)
);


CREATE TABLE tbl_courses
(
    course_id     varchar(36) NOT NULL,
    course_code   varchar(16) NOT NULL,
    university_id varchar(36) NOT NULL,

    PRIMARY KEY (course_id),
    FOREIGN KEY (university_id) REFERENCES tbl_universities (university_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
    UNIQUE (course_code, university_id)
);

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
    verification_code        varchar(64),
    verification_timestamp   datetime,
    verification_confirmed   int           NOT NULL,
    password_reset_code      varchar(64),
    password_reset_timestamp datetime,
    has_valid_profile        int           NOT NULL,
    card_color               varchar(15),
    date_of_birth            date,
    university_id            varchar(36),
    major                    varchar(64),
    standing                 varchar(32),
    gpa                      varchar(4),
    biography                text,
    profile_picture_url      varchar(512),

    PRIMARY KEY (user_id),
    FOREIGN KEY (university_id) REFERENCES tbl_universities (university_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
    UNIQUE (user_handle),
    UNIQUE (email),
    UNIQUE (verification_code)
);


CREATE TABLE tbl_registration
(
    registration_id varchar(36) NOT NULL,
    user_id         varchar(36) NOT NULL,
    course_id       varchar(36) NOT NULL,

    PRIMARY KEY (registration_id),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES tbl_courses (course_id) ON UPDATE NO ACTION ON DELETE CASCADE,
    UNIQUE (user_id, course_id)
);


CREATE TABLE tbl_media
(
    user_id   varchar(36)  NOT NULL,
    ordering  int          NOT NULL,
    media_url varchar(512) NOT NULL,

    PRIMARY KEY (media_url),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE NO ACTION ON DELETE CASCADE,
    UNIQUE (user_id, ordering)
);


CREATE TABLE tbl_relationships
(
    relationship_id     varchar(36) NOT NULL,
    user_id             varchar(36) NOT NULL,
    other_user_id       varchar(36) NOT NULL,
    relationship_status varchar(32) NOT NULL,
    rating              int,

    PRIMARY KEY (relationship_id),
    FOREIGN KEY (user_id) REFERENCES tbl_users (user_id) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY (other_user_id) REFERENCES tbl_users (user_id) ON UPDATE NO ACTION ON DELETE NO ACTION,
    UNIQUE (user_id, other_user_id)
);

CREATE INDEX idx_users_verification_timestamp ON tbl_users (verification_timestamp);