package controller;

import java.io.*;
import java.sql.*;
import java.util.*;

import com.google.gson.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.*;
import org.junit.rules.*;
import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;
import org.springframework.boot.SpringApplication;

import model.*;

public class ControllerTest {

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(10);

    private Savepoint savepoint;
    private static String apiHost;
    private static String apiBaseUrl;
    private static CloseableHttpClient httpClient;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        DatabaseConnectionPool.enableTesting();
        SpringApplication.run(RestServiceApplication.class);

        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));

        apiHost = configProps.getProperty("API_HOST");
        apiBaseUrl = configProps.getProperty("API_BASE_URL");

        httpClient = HttpClients.createDefault();
    }

    @AfterClass
    public static void tearDownAfterClass() throws IOException {
        DatabaseConnectionPool.disableTesting();
        httpClient.close();
    }

    @Before
    public void setUpBeforeTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        savepoint = dbconn.createSavepoint();
        DatabaseConnectionPool.releaseConnection(dbconn);
    }

    @After
    public void tearDownAfterTest() throws SQLException {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        dbconn.revertToSavepoint(savepoint);
        DatabaseConnectionPool.releaseConnection(dbconn);
    }

    public static ResponseEntity<JsonObject> convertHttpResponseToResponseEntity(CloseableHttpResponse response) throws IOException {
        System.out.println(EntityUtils.toString(response.getEntity(), "UTF-8"));
        JsonObject responseBody = new JsonParser().parse(EntityUtils.toString(response.getEntity(), "UTF-8"))
                .getAsJsonObject();
        HttpStatus statusCode = HttpStatus.valueOf(response.getStatusLine().getStatusCode());

        return new ResponseEntity<>(responseBody, statusCode);

    }

    public static ResponseEntity<String> sendGetRequest(String apiPathUrl, Map<String, String> parameters) throws IOException {
        StringJoiner sj = new StringJoiner("&", apiHost + apiBaseUrl + apiPathUrl + "?", "");
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
        HttpPost post = new HttpPost(apiHost + apiBaseUrl + apiPathUrl);
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

    @Test
    public void getTest() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("code", "z4zFXzHav1WsHmxiQ5EVbUcJVoTVcKbub9PsaL6rh8zF1y9HXd6BXpCQ9T6np2MH");

        ResponseEntity<String> x = sendGetRequest("/user/verify", parameters);
        System.out.println(x);
    }
}