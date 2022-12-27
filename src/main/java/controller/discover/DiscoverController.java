package controller.discover;

import java.io.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import model.*;
import services.*;
import static helpers.Utilities.*;

@RestController
@RequestMapping("/discover")
public class DiscoverController {

    // Token authentication service
    private final AuthTokenService authTokenService;

    /**
     * Initializes controller
     */
    public DiscoverController() throws IOException {
        authTokenService = new AuthTokenService();
    }

    /**
     * User likes other user
     *
     * @param payload JSON object containing "userId", "accessToken", "otherUserId" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/like",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> likeUser(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String otherUserId = payload.get("otherUserId");

            return createStatusJSON(dbconn.transaction_likeUser(userId, otherUserId));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * User dislikes other user
     *
     * @param payload JSON object containing "userId", "accessToken", "otherUserId" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/dislike",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> dislikeUser(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String otherUserId = payload.get("otherUserId");

            return createStatusJSON(dbconn.transaction_dislikeUser(userId, otherUserId));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * User rates other user
     *
     * @param payload JSON object containing "userId", "accessToken", "otherUserId", "rating" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/rate",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> rateUser(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String otherUserId = payload.get("otherUserId");
            int rating = Integer.parseInt(payload.get("rating"));

            return createStatusJSON(dbconn.transaction_rateUser(userId, otherUserId, rating));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }

    /**
     * User blocks other user
     *
     * @param payload JSON object containing "userId", "accessToken", "otherUserId" fields
     * @apiNote POST request
     *
     * @return JSON object containing boolean. 200 status code iff success
     */
    @RequestMapping(path = "/block",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE,
        method = RequestMethod.POST)
    public ResponseEntity<Object> blockUser(@RequestBody Map<String, String> payload) {

        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();

		try {
            String userId = payload.get("userId");
            String accessToken = payload.get("accessToken");

            // Verifies access token
            if (!authTokenService.verifyAccessToken(userId, accessToken)) {
                return createStatusJSON("Invalid access token", HttpStatus.UNAUTHORIZED);
            }

            String otherUserId = payload.get("otherUserId");

            return createStatusJSON(dbconn.transaction_blockUser(userId, otherUserId));

        } finally {
            DatabaseConnectionPool.releaseConnection(dbconn);
        }
    }
}
