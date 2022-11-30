package helpers;

import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;

import java.util.*;

public final class Utilities {
    public static String loadTemplate(String fileName) {
        try {
            return Jsoup.parse(ResourceUtils.getFile("classpath:templates/" + fileName), "UTF-8").toString();
        } catch (Exception e) {
            return "Failed to load HTML template";
        }
    }

    public static Map<String, String> generateMap(String... args) {
        Map<String, String> response = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            response.put(args[i], args[i + 1]);
        }
        return response;
    }

    public static ResponseEntity<Object> createJSONResponseEntity(String body, HttpStatus status) {
        return new ResponseEntity<>(generateMap("body", body), status);
    }

    public static ResponseEntity<Object> createJSONResponseEntity(ResponseEntity<String> responseEntity) {
        return createJSONResponseEntity(responseEntity.getBody(), responseEntity.getStatusCode());
    }
}
