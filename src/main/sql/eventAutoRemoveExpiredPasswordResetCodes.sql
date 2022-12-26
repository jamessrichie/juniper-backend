CREATE EVENT evt_auto_remove_expired_password_reset_codes
    ON SCHEDULE EVERY 5 MINUTE STARTS CURRENT_TIMESTAMP
    ON COMPLETION PRESERVE
    DO UPDATE LOW_PRIORITY
       tbl_users
       SET password_reset_code = null,
           password_reset_timestamp = null,
           has_reset_password = null
       WHERE (password_reset_timestamp IS NOT NULL AND password_reset_timestamp < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 15 MINUTE))
              OR has_reset_password = true;