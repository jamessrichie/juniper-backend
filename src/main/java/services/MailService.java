package services;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static java.lang.Integer.*;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import org.springframework.http.*;
import org.springframework.mail.javamail.*;
import org.springframework.util.ResourceUtils;
import static org.apache.commons.lang3.StringUtils.*;

import helpers.Utilities;

public class MailService {

    private final String API_HOST;
    private final ThreadPoolExecutor executor;
    private final JavaMailSenderImpl mailSender;

    public MailService() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:properties/api.properties")));
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/smtp.credentials")));

        API_HOST = configProps.getProperty("API_HOST");

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

        executor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Builds emails and sends them in a new thread
     *
     * @param toAddress recipient address
     * @param subject email subject
     * @param content email body
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

        executor.execute(() -> mailSender.send(message));
    }

    /**
     * Sends a verification code to the recipient
     *
     * @param name recipient name
     * @param toAddress recipient address
     * @param verificationCode recipient user's verification code
     *
     * @return JSON object containing status message. 200 status code iff success
     */
    public ResponseEntity<Object> sendVerificationEmail(String name, String toAddress, String verificationCode) {
        try {
            String subject = "Please verify your email";
            String content = Utilities.loadTemplate("verification/verification_email.html");
            String verificationUrl = API_HOST + "/user/verify?code=" + verificationCode;

            content = content.replace("[[name]]", substringBefore(name, " "));
            content = content.replace("[[url]]", verificationUrl);
            content = content.replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));

            sendEmail(toAddress, subject, content);

            return Utilities.createJSONWithStatusMessage("Successfully sent verification email", HttpStatus.OK);

        } catch (Exception e) {
            return Utilities.createJSONWithStatusMessage("Failed to send verification email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
