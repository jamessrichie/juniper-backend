package controller.version;

import java.io.*;
import java.util.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.ResourceUtils;

@RestController
@RequestMapping("/version")
public class VersionController {

    private static String apiVersion;

    public VersionController() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));

        apiVersion = configProps.getProperty("API_VERSION");
    }

    /**
     * Gets the current API version
     *
     * @return HTML page. 200 status code iff success
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> getApiVersion() {
        return new ResponseEntity<>(apiVersion, HttpStatus.OK);
    }
}
