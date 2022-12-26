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

import static helpers.Utilities.*;

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

        executor = new ThreadPoolExecutor(5, 20, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * Builds emails and sends them in a new thread
     *
     * @param toAddress recipient address
     * @param subject email subject
     * @param content email body
     */
    private void sendEmail(String toAddress, String subject, String content, String name, String url) throws MessagingException, UnsupportedEncodingException {
        String fromAddress = "support@thejuniperapp.com";
        String senderName = "The Juniper App";

        content = content.replace("[[name]]", substringBefore(name, " "));
        content = content.replace("[[url]]", url);
        content = content.replace("[[year]]", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

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
            String content = loadTemplate("verification_email.html");
            String verificationUrl = API_HOST + "/user/verify?code=" + verificationCode;

            sendEmail(toAddress, subject, content, name, verificationUrl);

            return createStatusJSON("Successfully sent email", HttpStatus.OK);

        } catch (Exception e) {
            return createStatusJSON("Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends a password reset code to the recipient
     *
     * @param name recipient name
     * @param toAddress recipient address
     * @param passwordResetCode recipient user's password reset code
     *
     * @return JSON object containing status message. 200 status code iff success
     */
    public ResponseEntity<Object> sendPasswordResetEmail(String name, String toAddress, String passwordResetCode) {
        try {
            String subject = "Password reset request";
            String content = loadTemplate("password_reset_email.html");
            String passwordResetUrl = API_HOST + "/auth/reset?code=" + passwordResetCode;

            sendEmail(toAddress, subject, content, name, passwordResetUrl);

            return createStatusJSON("Successfully sent email", HttpStatus.OK);

        } catch (Exception e) {
            return createStatusJSON("Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
