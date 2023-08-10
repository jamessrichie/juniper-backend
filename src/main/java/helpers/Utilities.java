package helpers;

import java.io.*;
import java.util.*;
import java.security.SecureRandom;

import com.google.gson.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.apache.http.client.methods.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;


public final class Utilities {

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

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
        int leftLimit = 48;
        int rightLimit = 122;

        try {
            SecureRandom secureRandom = SecureRandom.getInstanceStrong();

            return secureRandom.ints(leftLimit, rightLimit + 1)
              .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
              .limit(length)
              .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
              .toString();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ResponseEntity<String> sendGetRequest(String apiPathUrl, Map<String, String> parameters) throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));

        String localHost = configProps.getProperty("LOCAL_HOST");

        StringJoiner sj = new StringJoiner("&", localHost + apiPathUrl + "?", "");
        for (String key : parameters.keySet()) {
            sj.add(key + "=" + parameters.get(key));
        }
        String url = sj.toString();
        System.out.println(url);
        HttpGet get = new HttpGet(url);

        CloseableHttpResponse response = httpClient.execute(get);

        String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
        HttpStatus statusCode = HttpStatus.valueOf(response.getStatusLine().getStatusCode());

        return new ResponseEntity<>(responseBody, statusCode);
    }

    public static ResponseEntity<JsonObject> sendPostRequest(String apiPathUrl, Map<String, Object> body) throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));

        String localHost = configProps.getProperty("LOCAL_HOST");

        HttpPost post = new HttpPost(localHost + apiPathUrl);
        StringEntity entity = new StringEntity(new Gson().toJson(body));
        post.setEntity(entity);
        post.setHeader("Accept", "application/json");
        post.setHeader("Content-type", "application/json");

        CloseableHttpResponse response = httpClient.execute(post);

        JsonObject responseBody = new JsonParser().parse(EntityUtils.toString(response.getEntity(), "UTF-8"))
                .getAsJsonObject();
        HttpStatus statusCode = HttpStatus.valueOf(response.getStatusLine().getStatusCode());

        return new ResponseEntity<>(responseBody, statusCode);
    }

    /**
     * Extracts a value from a JSON object given its key
     */
    public static Object extractValueFromJsonObject(JsonObject json, String key) {
        return json.get(key);
    }

    /**
     * Extracts a string from a JSON object given its key
     */
    public static String extractStringFromJsonObject(JsonObject json, String key) {
        return extractValueFromJsonObject(json, key).toString().replaceAll("\"", "");
    }
}
