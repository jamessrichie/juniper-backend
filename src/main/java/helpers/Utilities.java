package helpers;

import org.jsoup.Jsoup;
import org.springframework.util.ResourceUtils;

public final class Utilities {
    public static String loadTemplate(String fileName) {
        try {
            return Jsoup.parse(ResourceUtils.getFile("classpath:templates/" + fileName), "UTF-8").toString();
        } catch (Exception e) {
            return "Failed to load HTML template";
        }
    }
}
