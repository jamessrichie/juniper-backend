CREATE EVENT evt_auto_delete_unverified_users
    ON SCHEDULE EVERY 30 MINUTE STARTS CURRENT_TIMESTAMP
    ON COMPLETION PRESERVE
    DO DELETE LOW_PRIORITY
       FROM tbl_users
       WHERE verification_timestamp < DATE_SUB(NOW(), INTERVAL 24 HOUR)
         AND has_verified_email = false;