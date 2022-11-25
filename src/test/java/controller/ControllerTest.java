package controller;

import java.io.*;
import java.sql.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.*;
import org.apache.http.impl.client.*;
import org.apache.http.client.methods.*;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ResourceUtils;

import model.*;

public class ControllerTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(10);

    public static String apiHost;
    public static String apiBaseUrl;

    public static CloseableHttpClient client;
    public static DatabaseConnection dbconn;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException, SQLException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));

        apiHost = configProps.getProperty("API_HOST");
        apiBaseUrl = configProps.getProperty("API_BASE_URL");

        client = HttpClients.createDefault();
        dbconn = new DatabaseConnection();
    }

    @AfterClass
    public static void tearDownAfterClass() throws IOException {
        client.close();
    }

    public static ResponseEntity<String> sendPOST(String apiPathUrl, Map<String, String> parameters) throws IOException {
        StringJoiner sj = new StringJoiner("&", apiHost + apiBaseUrl + apiPathUrl + "?", "");
        for (String key : parameters.keySet()) {
            sj.add(key + "=" + parameters.get(key));
        }
        String postUrl = sj.toString();

        System.out.println(postUrl);

        HttpPost post = new HttpPost(postUrl);
        try (CloseableHttpResponse response = client.execute(post)) {
            System.out.println(response.getStatusLine());
        }
        return null;
    }

    @Test
    public void postTest() throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("name", "name");
        parameters.put("email", "email@domain.com");
        parameters.put("password", "password");

        sendPOST("/user/createUser", parameters);
    }
}
