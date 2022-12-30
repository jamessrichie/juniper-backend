package controller.services;

import java.io.*;
import java.util.*;

import com.google.gson.JsonObject;
import org.springframework.http.*;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import static helpers.Utilities.*;

@RestController
@RequestMapping("/services")
public class ServicesController {

    /**
     * Resends the most recent email sent
     */
    @RequestMapping(path = "/resend-email",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> resendEmail(@RequestBody Map<String, Object> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String email = payload.get("email").toString().toLowerCase();

            ResponseEntity<String> resolveEmailToMostRecentEmailTypeStatus = dbconn.transaction_resolveEmailToMostRecentEmailType(email);
            String mostRecentEmailType = resolveEmailToMostRecentEmailTypeStatus.getBody();

            String redirectUrl;

            if (mostRecentEmailType == null) {
                // If user does not exist, vaguely claim that email has been sent
                return createStatusJSON("Successfully sent email", HttpStatus.OK);

            } else if (mostRecentEmailType.equals("verification")) {
                redirectUrl = "/user/request-account-verification";

            } else if (mostRecentEmailType.equals("password_reset")) {
                redirectUrl = "/auth/request-reset-password";

            } else {
                throw new RuntimeException("Unrecognized email type: " + mostRecentEmailType);
            }
            ResponseEntity<JsonObject> postResponse = sendPostRequest(redirectUrl, payload);
            return createStatusJSON(extractStringFromJsonObject(postResponse.getBody(), "status"), postResponse.getStatusCode());

        } catch (IOException e) {
            e.printStackTrace();
            return createStatusJSON("Request failed", HttpStatus.INTERNAL_SERVER_ERROR);

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }


}
