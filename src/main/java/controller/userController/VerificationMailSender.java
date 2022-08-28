package controller.userController;

import java.io.*;
import java.util.*;
import javax.mail.internet.MimeMessage;
import static java.lang.Integer.parseInt;

import org.jsoup.Jsoup;
import org.springframework.http.*;
import org.springframework.mail.javamail.*;
import org.springframework.util.ResourceUtils;
import static org.apache.commons.lang3.StringUtils.*;

public class VerificationMailSender {

    private final JavaMailSenderImpl mailSender;

    public VerificationMailSender() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:connections/smtp.properties")));

        mailSender = new JavaMailSenderImpl();
		mailSender.setHost(configProps.getProperty("SMTP_HOST"));
		mailSender.setPort(parseInt(configProps.getProperty("SMTP_PORT")));
		mailSender.setUsername(configProps.getProperty("SMTP_ADDRESS"));
		mailSender.setPassword(configProps.getProperty("SMTP_PASSWORD"));

        Properties props = mailSender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.debug", "true");
        props.put("mail.smtp.allow8bitmime", "true");
        props.put("mail.smtps.allow8bitmime", "true");
    }

    public ResponseEntity<String> sendVerificationEmail(String name, String toAddress, String verificationCode) {
        try {
            String fromAddress = "support@thejuniperapp.com";
            String senderName = "The Juniper App";
            String subject = "Please verify your email";
            String content = Jsoup.parse(ResourceUtils.getFile("classpath:templates/verification_email.html"), "UTF-8").toString();

            String verificationURL = "http://localhost:8080/user/verifyUser?code=" + verificationCode;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);

            helper.setFrom(fromAddress, senderName);
            helper.setTo(toAddress);
            helper.setSubject(subject);

            content = content.replace("[[name]]", substringBefore(name, " "));
            content = content.replace("[[url]]", verificationURL);
            content = content.replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
            helper.setText(content, true);

            mailSender.send(message);

            return new ResponseEntity<>("Successfully sent verification email", HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Failed to send verification email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
