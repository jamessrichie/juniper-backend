package services;

import java.io.*;
import java.util.*;
import static java.lang.Integer.*;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.http.*;
import org.springframework.mail.javamail.*;
import org.springframework.util.ResourceUtils;
import static org.apache.commons.lang3.StringUtils.*;

import helpers.Utilities;

public class MailService {

    private final String apiHost;
    private final JavaMailSenderImpl mailSender;

    public MailService() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/smtp.credentials")));

        apiHost = configProps.getProperty("API_HOST");

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

    /**
     * Builds and sends emails
     */
    private void sendEmail(String toAddress, String subject, String content) throws MessagingException, UnsupportedEncodingException {
        String fromAddress = "support@thejuniperapp.com";
        String senderName = "The Juniper App";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        helper.setFrom(fromAddress, senderName);
        helper.setTo(toAddress);
        helper.setSubject(subject);
        helper.setText(content, true);

        mailSender.send(message);
    }

    /**
     * Sends a verification code to the recipient
     */
    public ResponseEntity<String> sendVerificationEmail(String name, String toAddress, String verificationCode) {
        try {
            String subject = "Please verify your email";
            String content = Utilities.loadTemplate("verification_email.html");
            String verificationUrl = apiHost + "/user/verifyEmail?code=" + verificationCode;

            content = content.replace("[[name]]", substringBefore(name, " "));
            content = content.replace("[[url]]", verificationUrl);
            content = content.replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));

            sendEmail(toAddress, subject, content);

            return new ResponseEntity<>("Successfully sent verification email", HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Failed to send verification email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
