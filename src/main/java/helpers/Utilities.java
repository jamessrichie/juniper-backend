package helpers;

import java.util.*;
import java.security.SecureRandom;

import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;

public final class Utilities {

    /**
     * Loads an HTML template from resources/templates
     *
     * @return HTML file as String
     */
    public static String loadTemplate(String fileName) {
        try {
            return Jsoup.parse(ResourceUtils.getFile("classpath:templates/" + fileName), "UTF-8").toString();
        } catch (Exception e) {
            return "Failed to load HTML template";
        }
    }

    /**
     * Generates a String-String map
     *
     * @param args must be a series of String pairs, where first String is key, and second String is value
     * @return a HashMap
     */
    public static Map<String, String> generateMap(String... args) {
        Map<String, String> response = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            response.put(args[i], args[i + 1]);
        }
        return response;
    }

    /**
     * Generates a JSON object containing a status message
     */
    public static ResponseEntity<Object> createStatusJSON(String status, HttpStatus statusCode) {
        return new ResponseEntity<>(generateMap("status", status), statusCode);
    }

    /**
     * Generates a JSON object containing a status message
     */
    public static <T> ResponseEntity<Object> createStatusJSON(ResponseEntity<T> responseEntity) {
        return createStatusJSON(responseEntity.getBody().toString(), responseEntity.getStatusCode());
    }

    /**
     * Generates a cryptographically secure random string of specified length
     */
    public static String generateSecureString(int length) {
        SecureRandom random = new SecureRandom();

        byte[] bytes = new byte[length];
        random.nextBytes(bytes);

        return new String(bytes);
    }
}
